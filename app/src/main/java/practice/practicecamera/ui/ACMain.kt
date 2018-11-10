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
        val camFrag = FMCamera.newInstance(config = VMCamera.UIConfiguration.Selfie)
        supportFragmentManager.beginTransaction()
                .add(camFrag, "")
                .replace(R.id.acMainFullFragLyt, camFrag)
                .addToBackStack(null)
                .commit()
    }
}
