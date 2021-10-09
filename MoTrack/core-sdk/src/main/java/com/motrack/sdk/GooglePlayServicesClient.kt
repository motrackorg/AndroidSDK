package com.motrack.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author yaya (@yahyalmh)
 * @since 09th October 2021
 */

class GooglePlayServicesClient {
    companion object {

        public class GooglePlayServicesInfo internal constructor(
            val gpsAdid: String,
            val isTrackingEnabled: Boolean
        )

        @Throws(Exception::class)
        fun getGooglePlayServicesInfo(
            context: Context,
            timeoutMilliSec: Long
        ): GooglePlayServicesInfo? {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Google Play Services info can't be accessed from the main thread" }
            try {
                val pm = context.packageManager
                pm.getPackageInfo("com.android.vending", 0)
            } catch (e: Exception) {
                throw e
            }
            val connection: GooglePlayServicesConnection =
                GooglePlayServicesConnection(timeoutMilliSec)
            val intent = Intent("com.google.android.gms.ads.identifier.service.START")
            intent.setPackage("com.google.android.gms")
            if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                return try {
                    val gpsInterface: GooglePlayServicesInterface =
                        GooglePlayServicesInterface(
                            connection.binder
                        )
                    GooglePlayServicesInfo(
                        gpsInterface.gpsAdid!!,
                        gpsInterface.getTrackingEnabled(true)
                    )
                } catch (exception: Exception) {
                    throw exception
                } finally {
                    context.unbindService(connection)
                }
            }
            throw IOException("Google Play connection failed")
        }
    }

    private class GooglePlayServicesConnection(var timeoutMilliSec: Long) :
        ServiceConnection {
        var retrieved = false
        private val queue = LinkedBlockingQueue<IBinder>(1)
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try {
                queue.put(service)
            } catch (localInterruptedException: InterruptedException) {
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}

        @get:Throws(InterruptedException::class)
        val binder: IBinder
            get() {
                check(!retrieved)
                retrieved = true
                return queue.poll(timeoutMilliSec, TimeUnit.MILLISECONDS) as IBinder
            }

    }

    private class GooglePlayServicesInterface(private val binder: IBinder) : IInterface {
        override fun asBinder(): IBinder {
            return binder
        }

        @get:Throws(RemoteException::class)
        val gpsAdid: String?
            get() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                val id: String? = try {
                    data.writeInterfaceToken("com.google.android.gms.ads.identifier.internal.IAdvertisingIdService")
                    binder.transact(1, data, reply, 0)
                    reply.readException()
                    reply.readString()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
                return id
            }

        @Throws(RemoteException::class)
        fun getTrackingEnabled(paramBoolean: Boolean): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            val limitAdTracking: Boolean = try {
                data.writeInterfaceToken("com.google.android.gms.ads.identifier.internal.IAdvertisingIdService")
                data.writeInt(if (paramBoolean) 1 else 0)
                binder.transact(2, data, reply, 0)
                reply.readException()
                0 != reply.readInt()
            } finally {
                reply.recycle()
                data.recycle()
            }
            return !limitAdTracking
        }
    }
}