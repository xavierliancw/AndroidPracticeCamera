package practice.practicecamera.ui

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_fmcamera.*
import practice.practicecamera.R
import practice.practicecamera.special.camera.CameraControl
import practice.practicecamera.viewmodels.VMCamera

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
class FMCamera : Fragment(), CameraControl.CameraControlDelegate
{
    //region Private Properties

    private lateinit var vm: VMCamera
    private val cameraGremlin = CameraControl()

    //endregion
    //region Associated

    companion object
    {
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
        fmCameraTakePhotoBt.setOnClickListener {
            cameraGremlin.takePicture()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?)
    {
        super.onActivityCreated(savedInstanceState)
        cameraGremlin.performSetupWhenActivityCreated(activity = activity)
    }

    override fun onResume()
    {
        super.onResume()
        cameraGremlin.performActionsOnResume(fragment = this, textureVw = fmCameraTextureVw)
    }

    override fun onPause()
    {
        cameraGremlin.performActionsOnPause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray)
    {
        if (cameraGremlin.shouldHandleOnPermissionsRequestResult(requestCode = requestCode,
                                                                 grantResults = grantResults))
        {
            cameraGremlin.handleOnPermissionsRequestResult()
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    //endregion
    //region Private Functions


    //endregion
    //region Camera Control Conformance

    override fun cameraControlDidWantToExplainWhyCameraPermissionsAreNeeded(reason: String)
    {
        println("reason")
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cameraControlFinishedTakingPicture()
    {
        (activity as? ACMain)?.pushPhotoViewer()
    }

    //endregion
}
