package de.post.ident.internal_core.util.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import de.post.ident.internal_core.rest.CallbackUrlDTO
import de.post.ident.internal_core.util.log

interface EventBus<T: Any> {
    fun subscribe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit)
    fun subscribe(observer: (T) -> Unit)
    fun unsubscribe(observer: (T) -> Unit)
}

inline fun <T: Any, reified K : T> EventBus<T>.subscribeForEvent(lifecycleOwner: LifecycleOwner, crossinline observer: (K) -> Unit) {
    subscribe(lifecycleOwner) {
        if (it is K) {
            observer(it)
        }
    }
}

class EventBusSender<T: Any> : EventBus<T> {
    private val subscriptions = mutableSetOf<(T) -> Unit>()
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun subscribe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
        log("subscribed: ${observer.hashCode()}")
        synchronized(subscriptions) {
            subscriptions.add(observer)
        }
        lifecycleOwner.lifecycle.addObserver( object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                log("unsubscribed: ${observer.hashCode()}")
                synchronized(subscriptions) {
                    subscriptions.remove(observer)
                }
            }
        })
    }

    override fun subscribe(observer: (T) -> Unit) {
        subscriptions.add(observer)
    }

    override fun unsubscribe(observer: (T) -> Unit) {
        subscriptions.remove(observer)
    }

    fun sendEvent(event: T) {
        uiHandler.post {
            synchronized(subscriptions) {
                subscriptions.forEach {
                    it.invoke(event)
                }
            }
        }
    }
}

class CallbackUrlHandling(private val activity: Activity, private val callbackUrl: CallbackUrlDTO?) {
    fun startActivity(): Boolean {
        when {
            callbackUrl?.appUrl != null -> {
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(callbackUrl.appUrl))
                urlIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(urlIntent)
            }
            callbackUrl?.webUrl != null -> {
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(callbackUrl.webUrl))
                activity.startActivity(urlIntent)
            }
            else -> {
                return false
            }
        }
        return true
    }
}
