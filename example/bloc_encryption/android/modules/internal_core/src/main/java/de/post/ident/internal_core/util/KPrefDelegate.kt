package de.post.ident.internal_core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface PrefDelegate<T> : ReadWriteProperty<Any, T> {
    val key: String
}

class KPrefDelegate(private val prefs: SharedPreferences) {
    constructor(ctx: Context, name: String) : this(ctx.getSharedPreferences(name, Context.MODE_PRIVATE))

    private val moshi: Moshi = Moshi.Builder().build()

    fun string(key: String, defaultValue: String): PrefDelegate<String> = KStringDelegate(key, defaultValue)
    fun stringNA(key: String, initValue: String? = null): PrefDelegate<String?> = KStringNADelegate(key, initValue)
    fun boolean(key: String, defaultValue: Boolean): PrefDelegate<Boolean> = KBooleanDelegate(key, defaultValue)

    inline fun <reified T : Any> obj(key: String, defaultValue: T): PrefDelegate<T> = obj(getAdapter(T::class), key, defaultValue)
    fun <T : Any> obj(adapter: JsonAdapter<T>, key: String, defaultValue: T): PrefDelegate<T> = KObjectDelegate(adapter, key, defaultValue)

    inline fun <reified T : Any> objNA(key: String): PrefDelegate<T?> = objNA(key, T::class)
    fun <T : Any> objNA(key: String, clazz: KClass<T>): PrefDelegate<T?> = KObjectDelegateNA(getAdapter(clazz), key)

    fun <T : Any> objList(key: String, clazz: KClass<T>): PrefDelegate<List<T>> {
        val type = Types.newParameterizedType(List::class.java, clazz.java)
        return KObjectDelegate(getAdapter(type), key, emptyList())
    }

    fun <T : Any> getAdapter(clazz: KClass<T>): JsonAdapter<T> = moshi.adapter(clazz.java)
    fun <T : Any> getAdapter(type: ParameterizedType): JsonAdapter<T> = moshi.adapter(type)

    private inner class KStringDelegate(override val key: String, private val defaultValue: String) : PrefDelegate<String> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getString(key, null) ?: defaultValue
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) = prefs.edit { putString(key, value) }
    }

    private inner class KStringNADelegate(override val key: String, private val initValue: String? = null) : PrefDelegate<String?> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getString(key, null)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) = prefs.edit { putString(key, value) }
    }

    private inner class KBooleanDelegate(override val key: String, private val defaultValue: Boolean) : PrefDelegate<Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getBoolean(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = prefs.edit { putBoolean(key, value) }
    }

    private inner class KObjectDelegate<T : Any>(val adapter: JsonAdapter<T>, override val key: String, private val defaultValue: T) : PrefDelegate<T> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getString(key, null)?.let { adapter.fromJson(it) } ?: defaultValue
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) = prefs.edit { putString(key, adapter.toJson(value)) }
    }

    private inner class KObjectDelegateNA<T : Any>(val adapter: JsonAdapter<T>, override val key: String) : PrefDelegate<T?> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = prefs.getString(key, null)?.let { adapter.fromJson(it) }
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            prefs.edit {
                if (value != null) {
                    putString(key, adapter.toJson(value))
                } else {
                    remove(key)
                }
            }
        }
    }

    fun cleanup() {
        prefs.edit { clear() }
    }
}