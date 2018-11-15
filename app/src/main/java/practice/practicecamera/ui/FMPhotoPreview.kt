package practice.practicecamera.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_fmphoto_preview.*
import practice.practicecamera.R
import practice.practicecamera.special.camera.CameraControl
import java.io.File

class FMPhotoPreview : Fragment()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?
    {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_fmphoto_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        photoPreviewDismissBt.setOnClickListener {
            (activity as? ACMain)?.popNavStack()
        }
        photoPreviewRefreshBt.setOnClickListener {
            loadPic()
        }
    }

    private fun loadPic()
    {
        val lastPicUri = CameraControl.lastPicTaken
        if (lastPicUri == null)
        {
            println("FILE DOES NOT EXIST SDLKFJSL:DJFL:KSDJLJ")
            return
        }
        val lastPic = File(lastPicUri.path)
        if (lastPic.exists())
        {
            println(lastPicUri)
            val bMap = BitmapFactory.decodeFile(lastPicUri.path)
            photoPreviewImgVw.setImageBitmap(bMap)
        }
        else
        {
            println("DOES NOT EXIST UGHGHHGHGHGHGH")
        }
    }

    companion object
    {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment FMPhotoPreview.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
            FMPhotoPreview().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
