package practice.practicecamera.ui

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_fmcamera.*
import practice.practicecamera.R
import practice.practicecamera.viewmodels.VMCamera
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * The bundle key for the initial camera UI configuration.
 */
private const val BUNDLE_KEY_CAMERA_UI_CONFIGURATION = "camera ui config"

/**
 * This is a fragment that facilitates taking photos and selecting photos from the user's photo
 * library.
 *
 * Based off:
 * https://github.com/googlesamples/android-Camera2Basic/blob/master/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.java#L83
 */
class FMCamera : Fragment()
{
    //region Private Properties

    private lateinit var vm: VMCamera
    private var imgReader: ImageReader? = null   //Handles image capture
    private var oFile: File? = null //Output file for the picture
    private val outPutFileName = "mostRecentPicTaken.jpg"

    //endregion
    //region Private Camera Properties

    private lateinit var currentCameraID: String
    private var capSesh: CameraCaptureSession? = null
    private var currentCameraDevice: CameraDevice? = null
    private lateinit var previewSize: Size
    private var backgroundThread: HandlerThread? = null         //Prevents UI blocking
    private var backgroundHandler: Handler? = null              //Handles background tasks
    private var previewReqBuilder: CaptureRequest.Builder? = null
    private var previewReq: CaptureRequest? = null              //Gets generated by the req builder
    private var currentCamState = STATE_PREVIEW
    private val cameraOpenCloseSemaphore = Semaphore(1) //Prevents exits before cam shutoff
    private var flashIsSupported: Boolean = false
    private var sensorOrientation: Int? = null

    //endregion
    //region Private Camera Callback/Delegate Properties

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val oFile = oFile
        val nextImage = reader.acquireNextImage()
        if (oFile != null && nextImage != null)
        {
            backgroundHandler?.post(ImageSaver(nextImage, oFile))
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int)
        {
            activateCurrentCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int)
        {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val cameraStateCallback = object : CameraDevice.StateCallback()
    {
        override fun onOpened(cameraDevice: CameraDevice)
        {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseSemaphore.release()
            currentCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice)
        {
            cameraOpenCloseSemaphore.release()
            cameraDevice.close()
            currentCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int)
        {
            cameraOpenCloseSemaphore.release()
            cameraDevice.close()
            currentCameraDevice = null
            activity?.finish()
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val capSeshCallback = object : CameraCaptureSession.CaptureCallback()
    {
        private fun process(result: CaptureResult)
        {
            when (currentCamState)
            {
                STATE_PREVIEW                ->
                {
                    //Nothing to do when the camera preview is working normally.
                }
                STATE_WAITING_LOCK           ->
                {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == null)
                    {
                        captureStillPicture()
                    }
                    else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                             CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState)
                    {
                        // CONTROL_AE_STATE can be null on some devices
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                        {
                            currentCamState = STATE_PICTURE_TAKEN
                            captureStillPicture()
                        }
                        else
                        {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE     ->
                {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
                    {
                        currentCamState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE ->
                {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
                    {
                        currentCamState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult)
        {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult)
        {
            process(result)
        }

    }

    //endregion
    //region Associated

    companion object
    {
        //region Shared Properties
        //region Camera State "Enums"

        private const val STATE_PREVIEW = 0                 //Showing camera preview
        private const val STATE_WAITING_LOCK = 1            //Waiting for focus lock
        private const val STATE_WAITING_PRECAPTURE = 2      //Waiting for pre-capture exposure
        private const val STATE_WAITING_NON_PRECAPTURE = 3  //Waiting for any other pre-cap state
        private const val STATE_PICTURE_TAKEN = 4           //Pic was taken

        //endregion

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080
        private const val CAMERA_PERMISSION_REQ_CODE = 1

        /**
         * Android device manufacturers are inconsistent with how they install their camera
         * sensors. Sometimes, they're physically installed upside down or even sideways. This can
         * produce upside down/sideways pictures :/. For more info, look at whatever function uses
         * this property.
         */
        private val ORIENTATIONS = hashMapOf(Pair(Surface.ROTATION_0, 90),
                                             Pair(Surface.ROTATION_90, 0),
                                             Pair(Surface.ROTATION_180, 270),
                                             Pair(Surface.ROTATION_270, 180))

        //endregion
        //region Shared Functions

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param config The UI configuration this fragment should start with.
         * @return A new instance of fragment FMCamera.
         */
        @JvmStatic
        fun newInstance(config: VMCamera.UIConfiguration): FMCamera
        {
            return FMCamera().apply {
                arguments = Bundle().apply {
                    putSerializable(BUNDLE_KEY_CAMERA_UI_CONFIGURATION, config)
                }
            }
        }

        /**
         * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one
         * that is at least as large as the respective texture view size, and that is at most as
         * large as the respective max size, and whose aspect ratio matches with the specified
         * value. If such size doesn't exist, choose the largest one that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal {@code Size}, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(choices: List<Size>, textureViewWidth: Int,
                                      textureViewHeight: Int, maxWidth: Int, maxHeight: Int,
                                      aspectRatio: Size): Size
        {
            val w = aspectRatio.width
            val h = aspectRatio.height

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = arrayListOf<Size>()

            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = arrayListOf<Size>()

            for (option in choices)
            {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w)
                {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight)
                    {
                        bigEnough.add(option)
                    }
                    else
                    {
                        notBigEnough.add(option)
                    }
                }
            }
            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return when
            {
                bigEnough.size > 0    -> Collections.min(bigEnough, CompareSizesByArea())
                notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
                else                  ->
                {
                    //            Log.e(TAG, "Couldn't find any suitable preview size");
                    //TODO Log dis
                    choices[0]
                }
            }
        }

        //endregion
    }

    private class ImageSaver internal constructor(
        /**
         * The JPEG image
         */
        private val mImage: Image,
        /**
         * The file we save the image into.
         */
        private val mFile: File
    ) : Runnable
    {
        override fun run()
        {
            val buffer = mImage.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try
            {
                output = FileOutputStream(mFile)
                output.write(bytes)
            }
            catch (e: IOException)
            {
                e.printStackTrace()
                TODO() //Report error
            }
            finally
            {
                mImage.close()
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

    /**
     * Compares two Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size>
    {
        override fun compare(lhs: Size, rhs: Size): Int
        {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong()
                                         * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    //endregion
    //region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        //Grab view model
        vm = ViewModelProviders.of(this).get(VMCamera::class.java)

        //Grab configuration from initialization process
        arguments?.let {
            val possUIConfig = it.getSerializable(
                    BUNDLE_KEY_CAMERA_UI_CONFIGURATION
            ) as? VMCamera.UIConfiguration
            if (possUIConfig != null)
            {
                vm.currentUIConfig = possUIConfig
            }
            else
            {
                TODO() //Report an error
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?
    {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_fmcamera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        button.setOnClickListener {
            println("SNAP FOTO!")
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?)
    {
        super.onActivityCreated(savedInstanceState)
        val activity = activity

        if (activity != null)
        {
            oFile = File(activity.getExternalFilesDir(null), outPutFileName)
        }
    }

    override fun onResume()
    {
        super.onResume()
        startBackgroundThread()
        val textureVw: AutoFitTextureView? = fmCameraTextureVw

        if (textureVw != null)
        {
            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can
            // open a camera and start preview from here (otherwise, we wait until the surface is
            // ready in the SurfaceTextureListener).
            if (textureVw.isAvailable)
            {
                activateCurrentCamera(textureVw.width, textureVw.height)
            }
            else
            {
                textureVw.surfaceTextureListener = surfaceTextureListener
            }
        }
    }

    override fun onPause()
    {
        deactivateCurrentCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQ_CODE)
        {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                TODO() //Output R.string.request_permission which doesn't exist yet
            }
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    //endregion
    //region Private Functions

    private fun requestCameraPermission()
    {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
        {
            TODO()  //Ask for permissions
        }
        else
        {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQ_CODE);
        }
    }

    /**
     * Sets up member variables related to the camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int)
    {
        val activity = activity ?: return
        val textureVw = fmCameraTextureVw ?: return
        val manager: CameraManager = activity.getSystemService(Context.CAMERA_SERVICE)
                                             as? CameraManager ?: return
        val byArea = Comparator<Size> { lhs, rhs ->
            // We cast here to ensure the multiplications won't overflow
            java.lang.Long.signum(lhs.width.toLong() *
                                  lhs.height - rhs.width.toLong() *
                                  rhs.height)
        }

        try
        {
            for (someCamID in manager.cameraIdList)
            {
                val charistics: CameraCharacteristics = manager.getCameraCharacteristics(someCamID)
                val rearFacing: Int = charistics.get(CameraCharacteristics.LENS_FACING) ?: continue
//                if (rearFacing != null && rearFacing == CameraCharacteristics.LENS_FACING_FRONT)
//                {
//                    continue TODO
//                }
                val map: StreamConfigurationMap = charistics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue
                val outputSizes = map.getOutputSizes(ImageFormat.JPEG) ?: continue
                val largestSize = outputSizes.maxWith(comparator = byArea) ?: continue

                imgReader = ImageReader.newInstance(largestSize.width, largestSize.height,
                                                    ImageFormat.JPEG, 2)
                imgReader?.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)

                //Dimens sometimes swap to get a correct preview relative to sensor coordinate
                var dimensAreSwapped = false
                val displayRotation = activity.windowManager.defaultDisplay.rotation
                val orient = charistics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                sensorOrientation = orient
                if (orient != null)
                {
                    dimensAreSwapped = when (displayRotation)
                    {
                        Surface.ROTATION_0, Surface.ROTATION_180  -> orient == 90 || orient == 270
                        Surface.ROTATION_90, Surface.ROTATION_270 -> orient == 0 || orient == 180
                        else                                      -> TODO() //Report invalid rotation "Display rotation is invalid: " + displayRotation
                    }
                }
                val displaySz = Point(); activity.windowManager.defaultDisplay.getSize(displaySz)
                var maxPreviewWidth = displaySz.x
                var maxPreviewHeight = displaySz.y
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height

                if (dimensAreSwapped)
                {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySz.y
                    maxPreviewHeight = displaySz.x
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH)
                {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT)
                {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }
                //Attempting to use too large a preview size could  exceed the camera bus'
                //bandwidth limitation, resulting in gorgeous previews but the storage of garbage
                //capture data
                previewSize = chooseOptimalSize(outputSizes.toList(), rotatedPreviewWidth,
                                                rotatedPreviewHeight, maxPreviewWidth,
                                                maxPreviewHeight, largestSize)

                //Fit the aspect ratio of TextureView to the chosen size for the preview
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                {
                    textureVw.setAspectRatio(previewSize.width, previewSize.height)
                }
                else
                {
                    textureVw.setAspectRatio(previewSize.height, previewSize.width)
                }
                // Check if the flash is supported.
                flashIsSupported = charistics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        ?: false

                //Finally, remember the camera ID
                currentCameraID = someCamID
            }
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO() //LOG DIS
        }
        catch (e: NullPointerException)
        {
            //Currently a NPE is thrown when the Camera2API is used but not supported on the
            //device this code runs.
            TODO() //FIGURE THIS OUT
        }
    }

    /**
     * Opens the camera specified by this class' current camera ID.
     */
    private fun activateCurrentCamera(width: Int, height: Int)
    {
        val act = activity as? Activity
        val mngr = act?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

        //Make sure appropriate stuff exists
        if (act == null || mngr == null)
        {
//            return
            TODO()  //Report the null
        }
        //Check if permissions exist
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {
            requestCameraPermission()
            return
        }
        //Proceed to set up camera(s)
        setUpCameraOutputs(width = width, height = height)
        configureTransform(width, height)
        try
        {
            if (!cameraOpenCloseSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS))
            {
                throw RuntimeException("Time out waiting to lock camera opening.")
                TODO() //Handleleee
            }
            mngr.openCamera(currentCameraID, cameraStateCallback, backgroundHandler)
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO() //Log dis
        }
        catch (e: InterruptedException)
        {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current camera specified by this class' current camera ID.
     */
    private fun deactivateCurrentCamera()
    {
        try
        {
            cameraOpenCloseSemaphore.acquire()
            capSesh?.close()
            capSesh = null

            currentCameraDevice?.close()
            currentCameraDevice = null

            imgReader?.close()
            imgReader = null
        }
        catch (e: InterruptedException)
        {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
            TODO() //alskdjflksdjflk
        }
        finally
        {
            cameraOpenCloseSemaphore.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread()
    {
        val newThread = HandlerThread("CameraBackground")
        backgroundThread = newThread
        newThread.start()
        backgroundHandler = Handler(newThread.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread()
    {
        backgroundThread?.quitSafely()
        try
        {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        }
        catch (e: InterruptedException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    /**
     * Creates a new capture session for the camera preview.
     */
    private fun createCameraPreviewSession()
    {
        try
        {
            val surfaceTexture: SurfaceTexture? = fmCameraTextureVw.surfaceTexture
            val currentCamera: CameraDevice? = currentCameraDevice
            val imgReader: ImageReader? = imgReader

            if (surfaceTexture == null || currentCamera == null || imgReader == null)
            {
                return
                TODO()  //alsdkjf;alksdjf
            }

            //Configure the size of default buffer to be the size of desired camera preview
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

            //This is the output surface we need to start the preview
            val surface = Surface(surfaceTexture)

            //Set up a capture request builder with the newly created Surface object
            previewReqBuilder = currentCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewReqBuilder?.addTarget(surface)

            //Now create a capture session for the visual preview and for the image reader
            currentCamera.createCaptureSession(
                    mutableListOf(surface, imgReader.surface),
                    object : CameraCaptureSession.StateCallback()
                    {
                        override fun onConfigureFailed(session: CameraCaptureSession)
                        {
                            TODO()//Say that the cap sesh initialization failed
                        }

                        override fun onConfigured(session: CameraCaptureSession)
                        {
                            val previewReqBuilder = previewReqBuilder
                            if (previewReqBuilder == null)
                            {
                                return
                                //TODO()
                            }
                            //The camera is already closed
                            if (currentCameraDevice == null)
                            {
                                return
                            }

                            //When the session is ready, start the preview
                            capSesh = session
                            try
                            {
                                //Set auto focus mode
                                previewReqBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                //Flash is automatically enabled when necessary
                                setAutoFlash(previewReqBuilder)

                                //Start displaying the preview safely
                                previewReq = previewReqBuilder.build()
                                val localizedPreviewReq = previewReq
                                if (localizedPreviewReq != null)
                                {
                                    capSesh?.setRepeatingRequest(
                                            localizedPreviewReq, capSeshCallback, backgroundHandler
                                    )
                                }
                            }
                            catch (e: CameraAccessException)
                            {
                                e.printStackTrace()
                                TODO()  //alskdjf;akdsjf
                            }
                        }
                    },
                    null
            )
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    /**
     * Configures the necessary matrix transformation to the texture view.
     * This method should be called after the camera preview size is determined and also when the
     * size of the texture view is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int)
    {
        val act = activity
        val textureView = fmCameraTextureVw

        if (textureView == null || act == null)
        {
            TODO()
            return
        }
        val rotation = act.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(),
                               previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
        {
            bufferRect.offset(centerX - bufferRect.centerX(),
                              centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90f * (rotation - 2).toFloat(), centerX, centerY)
        }
        else if (Surface.ROTATION_180 == rotation)
        {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    /**
     * Initiate a still image capture.
     */
    private fun takePicture()
    {
        lockFocus()
    }

    /**
     * The first step for a still image capture is to lock the focus.
     */
    private fun lockFocus()
    {
        try
        {
            val reqBuilder = previewReqBuilder ?: return

            //Tell the camera to lock its focus
            reqBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                           CameraMetadata.CONTROL_AF_TRIGGER_START)
            //Wait for the lock
            currentCamState = STATE_WAITING_LOCK
            capSesh?.capture(reqBuilder.build(), capSeshCallback, backgroundHandler)
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    /**
     * Run the pre-capture sequence for capturing a still image. This should be called when a
     * response from locking focus happens in the capture session callback.
     */
    private fun runPrecaptureSequence()
    {
        try
        {
            val reqBuilder = previewReqBuilder ?: return

            //Trigger the camera
            reqBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                           CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

            //Tell the cap session callback to wait for the pre-capture sequence to set
            currentCamState = STATE_WAITING_PRECAPTURE
            capSesh?.capture(reqBuilder.build(), capSeshCallback, backgroundHandler)
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    /**
     * Capture a still picture. This method should be called when a response happens from
     * locking focus occurs in the capture session callback.
     */
    private fun captureStillPicture()
    {
        try
        {
            val act = activity
            val theCamera = currentCameraDevice
            val imgReader = imgReader

            if (act == null || theCamera == null || imgReader == null)
            {
                TODO()
                return
            }
            //Create a capture request builder to take the photo
            val captureBuilder = theCamera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE
            )
            captureBuilder.addTarget(imgReader.surface)

            //Use the same AE and AF modes as the preview
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                               CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            setAutoFlash(captureBuilder)

            //Orientation
            val rotation = act.windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

            val capCallback = object : CameraCaptureSession.CaptureCallback()
            {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult)
                {
                    unlockFocus()   //TODO do something with the file
                    println("Saved ${oFile.toString()}")
                    super.onCaptureCompleted(session, request, result)
                }
            }
            capSesh?.stopRepeating()
            capSesh?.abortCaptures()
            capSesh?.capture(captureBuilder.build(), capCallback, null)
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int): Int
    {
        val sensorOrientation = sensorOrientation
        val manufacturerOrientation = ORIENTATIONS[rotation]
        if (sensorOrientation == null || manufacturerOrientation == null)
        {
            throw java.lang.NullPointerException("Sigh")
            TODO()
        }
        //Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        //Taking that into account, gotta rotate the JPEG properly.
        //For devices with orientation of 90, simply return the mapping from ORIENTATIONS.
        //For devices with orientation of 270, rotate the JPEG 180 degrees
        return (manufacturerOrientation + sensorOrientation + 270) % 360
    }

    /**
     * Unlock the focus. This method should be called when the still image capture sequence is
     * done.
     */
    private fun unlockFocus()
    {
        try
        {
            val previewReqBuilder = previewReqBuilder
            val previewReq = previewReq
            if (previewReqBuilder == null || previewReq == null)
            {
                return
                TODO()
            }
            //Reset the auto-focus trigger
            previewReqBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                  CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            setAutoFlash(previewReqBuilder)
            capSesh?.capture(previewReqBuilder.build(), capSeshCallback, backgroundHandler)

            //Revert to normal preview state
            currentCamState = STATE_PREVIEW
            capSesh?.setRepeatingRequest(previewReq, capSeshCallback, backgroundHandler)
        }
        catch (e: CameraAccessException)
        {
            e.printStackTrace()
            TODO()
        }
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder)
    {
        if (flashIsSupported)
        {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                               CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    //endregion
}
