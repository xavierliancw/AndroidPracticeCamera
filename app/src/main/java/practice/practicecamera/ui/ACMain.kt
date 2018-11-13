package practice.practicecamera.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import practice.practicecamera.R
import practice.practicecamera.viewmodels.VMCamera

class ACMain : AppCompatActivity()
{

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_acmain)
        pushCameraFrag()
    }

    private fun pushCameraFrag()
    {
        val camFrag = FMCamera.newInstance()
        supportFragmentManager.beginTransaction()
                .add(camFrag, "")
                .replace(R.id.acMainFullFragLyt, camFrag)
                .addToBackStack(null)
                .commit()
    }

    fun pushPhotoViewer()
    {
        val photoFrag = FMPhotoPreview.newInstance()
        supportFragmentManager.beginTransaction()
                .add(photoFrag, "")
                .replace(R.id.acMainFullFragLyt, photoFrag)
                .addToBackStack(null)
                .commit()
    }

    fun popNavStack()
    {
        supportFragmentManager.popBackStack()
    }
}
