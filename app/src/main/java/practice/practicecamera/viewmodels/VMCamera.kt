package practice.practicecamera.viewmodels

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import java.io.Serializable

class VMCamera(app: Application): AndroidViewModel(app)
{
    //region Properties

    var currentUIConfig: UIConfiguration = UIConfiguration.Standard

    //endregion
    //region Associated

    sealed class UIConfiguration: Serializable
    {
        object Standard: UIConfiguration()
        object Selfie: UIConfiguration()
    }

    //endregion
}
