package rumms

trait ControllerFactory {
	def newController(ctx:ControllerContext):Controller
}
