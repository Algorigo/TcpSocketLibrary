import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

object Rx2ServiceBindingFactory {
    fun <B : Binder> bind(context: Context, intent: Intent): Observable<B> {
        return bind(context, intent, Service.BIND_AUTO_CREATE)
    }

    fun <B : Binder> bind(context: Context, intent: Intent, flags: Int): Observable<B> {
        val subject = BehaviorSubject.create<B>().toSerialized()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                (binder as? B)?.let {
                    subject.onNext(it)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        return subject.doOnSubscribe {
            context.bindService(intent, serviceConnection, flags)
        }.doFinally {
            context.unbindService(serviceConnection)
        }
    }
}