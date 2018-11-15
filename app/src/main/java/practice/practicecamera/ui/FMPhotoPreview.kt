package practice.practicecamera.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.fragment_fmphoto_preview.*
import practice.practicecamera.R

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
    }

    override fun onResume()
    {
        super.onResume()
        val context = context ?: return
        val lastPic = PIC_FILE_NAME
        Glide.with(context)
                .load(lastPic)
                .into(photoPreviewImgVw)
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
