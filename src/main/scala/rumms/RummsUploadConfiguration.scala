package rumms

import java.io.File

import scutil.io.Files

case class RummsUploadConfiguration(
	location:File			= Files.TMP,
	maxFileSize:Long		= 256*1024*1024,
	maxRequestSize:Long		= 512*1024*1024,
	fileSizeThreshold:Int	= 1024*1024
)
