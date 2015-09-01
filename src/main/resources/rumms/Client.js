var rumms	= {};
rumms.userData	= @{USER_DATA};

/*// type hint
var ConversationContext = {
	// a new client has connected
	connected:		function(conversationId)
	// a client has disconnected
	disconnected:	function(conversationId)
	// the server uses a different protocol version than the client
	upgraded:		function(version)
	// the server sent a message
	message:		function(object)
	// the connection is alive
	heartbeat:	function()
	// the connection failed
	error:			function(object)
};
*/
rumms.Conversation	= function(context) {
	this.context		= context;
	this.conversationId = null;
	
	this.connected		= false;
	this.queue			= null;
	this.clientCont 	= null;
	this.serverCont 	= null;
	
	// non-null between push and call of commAbortFunc
	this.commAbortTimer	= null;
	// non-null during a client request
	this.commAbortFunc	= null;
};
rumms.Conversation.prototype = {
	version:		@{VERSION},
	encoding:		@{ENCODING},
	clientTTL:		@{CLIENT_TTL},
	servletPrefix:	@{SERVLET_PREFIX},
	
	/** delay before opening a new connection when the last opening failed */
	errorDelay:	10000,
		
	/** delay between queue polling */
	pollDelay:	100,
	
	/** delay to allow more messages to queue up */
	abortDelay: 200,
	
	//------------------------------------------------------------------------------
	//## public api
	
	connect: function() {
		this.hiLoop();
	},
	
	send: function(message) {
		this.queue.push({ id: this.clientCont, message: message });
		this.clientCont++;
		this.commImmediate();
	},
	
	//------------------------------------------------------------------------------
	//## private connect
	
	hiLoop: function() {
		var self	= this;
		
		var	client	= new XMLHttpRequest();
		client.open("POST", this.servletPrefix + "/hi?_=", true);
		client.onreadystatechange = function() {
			if (client.readyState !== 4)	return;
			clearTimeout(timer);
			try {
				if (client.status === 200) {
					self.hiSuccess(client.responseText);
				}
				else {
					self.notifyError("hi", "expected status 200, not " + client.status);
					self.hiError();
				}
			}
			catch (e) {
				self.notifyError("hi", "cannot get client status", e);
				self.hiError();
			}
		};
		
		// protect against requests hanging for a long time
		// cleared when the request has been sent
		var timer	= setTimeout(
			function() { client.abort(); },
			this.clientTTL
		);
		client.send(this.version);
	},
	
	hiSuccess: function(text) {
		this.queue			= [];
		this.clientCont 	= 0;
		this.serverCont 	= 0;
		
		function scan(s,p) {
			return s.substring(0, p.length) === p
					? s.substring(p.length) : null;
		}
	
		this.conversationId = scan(text, "OK ");
		if (this.conversationId === null) {
			this.connected	= false;
			var	version	= scan(text, "VERSION ");
			this.context.upgraded(version);
			return;
		}
		
		this.context.connected(this.conversationId);
			
		this.connected	= true;
		this.commLoop();
	},
	
	hiError: function(exception) {
		this.connected	= false;

		this.conversationId = null;
		
		this.queue			= null;
		this.clientCont 	= null;
		this.serverCont 	= null;
	},
	
	//------------------------------------------------------------------------------
	//## private message
	
	commLoop: function() {
		if (!this.connected)	return;
		
		var self	= this;
		
		var	client	= new XMLHttpRequest();
		client.open("POST", this.servletPrefix + "/comm?_=", true);
		client.onreadystatechange = function() {
			if (client.readyState !== 4)	return;
			
			clearTimeout(timer);
			this.commAbortFunc	= null;
			
			// a forced abort occurs if someone needs to send messages immediately
			if (forcedAbort) {
				self.commLoop();
				return;
			}

			try {
				if (client.status === 200) {
					self.commSuccess(client.responseText);
				}
				else {
					self.notifyError("comm", "expected status 200, not " + client.status);
					self.commError();
				}
			}
			catch (e) {
				self.notifyError("comm", "cannot get client status", e);
				self.commError();
				return;
			}
		};
		
		// don't interrupt, we're trying already
		this.commUnscheduleAbort();
		
		// protect against requests hanging for a long time
		// cleared when the request has been sent
		var timer	= setTimeout(
			function() { client.abort(); },
			this.clientTTL
		);
		// TODO limit how much is sent in one go?
		var outgoing	= this.queue.map(function(it) { return it.message; });
		client.send(JSON.stringify({
			"conversation": this.conversationId,
			// which messages have been sent to the server with this request
			"clientCont":	this.clientCont,
			// which messages the server told me will be next
			"serverCont":	this.serverCont,
			// new messages go to the server
			"messages":		outgoing//,
		}));
		
		// enable forced aborts when messages should be sent immediately
		var forcedAbort	= false;
		// sending messages may take quite some time, so we shouldn't interrupt that.
		// the server will not block when we sent new messages anyway.
		if (outgoing.length === 0) {
			this.commAbortFunc	= function() {
				forcedAbort	= true;
				client.abort();
			};
		}
	},
	
	// throttled connection aborts
	commImmediate: function() {
		this.commUnscheduleAbort();
		this.commScheduleAbort();
	},
	
	commScheduleAbort: function() {
		var self	= this;
		this.commAbortTimer	= setTimeout(
			function() {
				if (self.commAbortFunc) {
					self.commAbortFunc();
				}
				self.commAbortTimer	= null;
			},
			this.abortDelay
		);
	},
	
	commUnscheduleAbort: function() {
		if (this.commAbortTimer) {
			clearTimeout(this.commAbortTimer);
			this.commAbortTimer	= null;
		}
	},
		
	commSuccess: function(text) {
		// HACK for "messages has no properties" failure
		if (text === "") {
			this.notifyError("comm", "empty response");
			this.commLater(this.errorDelay);
			return;
		}
		
		// the server did not recognize our conversation id
		if (text === "CONNECT") {
			this.connected	= false;
			this.context.disconnected(this.conversationId);
			return;
		}
		
		try {
			this.context.heartbeat();
		}
		catch (e) {
			this.notifyError("comm", "exception in heartbeat handler", e);
			this.commLater(this.errorDelay);
		}
			
		try {
			var data	= JSON.parse(text);
			
			// which messages to ask the server for on the next request
			this.serverCont = data.serverCont;

			// remove messages the server has already seen
			this.queue	= this.queue.filter(function(it) { return it.id >= data.clientCont; });
			
			// publish new messages from the server to our context
			var messages	= data.messages;
			var self	= this;
			messages.forEach(function(it) {
				try {
					self.context.message(it);
				}
				catch (e) {
					self.notifyError("comm", "exception in message handler", e, it);
				}
			});
			this.commLater(this.pollDelay);
		}
		catch (e) {
			this.notifyError("comm", "exception in message parser", e, text);
			this.commLater(this.errorDelay);
		}
	},
	
	commError: function(exception) {
		this.commLater(this.errorDelay);
	},
	
	commLater: function(delay) {
		var self	= this;
		setTimeout(
			function() { self.commLoop(); },
			delay
		);
	},
	
	//------------------------------------------------------------------------------
	//## private util
	
	notifyError: function(method, description, exception, details) {
		this.context.error({
			method:			method,
			description:	description,
			exception:		exception	|| null,
			details:		details		|| null
		});
	}//,
};
