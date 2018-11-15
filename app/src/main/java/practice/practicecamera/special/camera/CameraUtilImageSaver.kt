package practice.practicecamera.special.camera

import android.media.Image
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CameraUtilImageSaver internal constructor(
    /**
     * The JPEG image.
     */
    private val imgToSave: Image,
    /**
     * The file to save the image into.
     */
    private val imgOFile: File
) : Runnable
{
    override fun run()
    {
        val buffer = imgToSave.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var output: FileOutputStream? = null
        try
        {
            output = FileOutputStream(imgOFile, false)
            output.write(bytes)
        }
        catch (e: IOException)
        {
            e.printStackTrace()
            TODO() //Report error
        }
        finally
        {
            imgToSave.close()
            if (null != output)
            {
                try
                {
                    output.close()
                }
                catch (e: IOException)
                {
                    e.printStackTrace()
                    TODO() //Report error
                }
            }
        }
    }
}
