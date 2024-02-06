package de.post.ident.internal_core.permission

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.R
import de.post.ident.internal_core.databinding.PiFragmentPermissionBinding
import de.post.ident.internal_core.databinding.PiPermissionItemBinding
import de.post.ident.internal_core.reporting.*
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.NFCStatus
import de.post.ident.internal_core.util.getNFCStatus
import de.post.ident.internal_core.util.showBackButton


@Keep
enum class UserPermission(val androidPermission: String, val titleKey: String, val descriptionKey: String, @DrawableRes val iconRes: Int, @DrawableRes val iconResInactive: Int, val reportingEvent: LogEvent?, val reportingEventResult: LogEvent?, val requiredSinceSdkLevel: Int = Build.VERSION_CODES.LOLLIPOP) {
    STORAGE(Manifest.permission.WRITE_EXTERNAL_STORAGE, "permissions_storage_title", "permissions_storage_description",
            R.drawable.pi_storage, R.drawable.pi_storage,null, null, Build.VERSION_CODES.LOLLIPOP),
    CAMERA(Manifest.permission.CAMERA, "permissions_camera_title", "permissions_camera_description",
            R.drawable.pi_camera, R.drawable.pi_camera_inactive, LogEvent.MR_CAM_PERMISSION, LogEvent.MR_CAM_PERMISSION_RESULT, Build.VERSION_CODES.LOLLIPOP),
    MICROPHONE(Manifest.permission.RECORD_AUDIO, "permissions_microphone_title", "permissions_microphone_description",
            R.drawable.pi_microphone, R.drawable.pi_microphone_inactive, LogEvent.MR_MIC_PERMISSION, LogEvent.MR_MIC_PERMISSION_CHECK_RESULT, Build.VERSION_CODES.LOLLIPOP),
    NFC(Manifest.permission.NFC, "eid_nfc_permission_title", "eid_nfc_permission_subtitle",
            R.drawable.pi_nfc, R.drawable.pi_nfc, null, null, Build.VERSION_CODES.LOLLIPOP),
    NOTIFICATION(Manifest.permission.POST_NOTIFICATIONS, "important_notification_channel_title", "push_channel_service_description",
        R.drawable.pi_notification, R.drawable.pi_notification_off, null, null, Build.VERSION_CODES.TIRAMISU)
}

@JsonClass(generateAdapter = true)
data class PermissionHelpData(val title: String, val description: String)

@JsonClass(generateAdapter = true)
data class PermissionFragmentParameter(val identMethod: IdentMethod, var permissionList: MutableList<UserPermission>, val helpData: PermissionHelpData?)

object PermissionUtil {
    fun hasPermissions(context: Context, permissions: List<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (permission == UserPermission.NFC.androidPermission && getNFCStatus(context) == NFCStatus.DISABLED) {
                    return false
                }
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    fun arePermissionsDeniedPermanently(act: Activity, permissions: List<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(act, permission)) {
                    return true
                }
            }
        }
        return false
    }
}

class PermissionFragment : DialogFragment() {
    companion object {
        private val BUNDLE_PAGE: BundleParameter<PermissionFragmentParameter> = BundleParameter.moshi(CoreEmmiService.moshi, "PermissionData")
        fun newInstance(identMethod: IdentMethod, permissionList: MutableList<UserPermission>, helpData: PermissionHelpData? = null): PermissionFragment = PermissionFragment()
                .withParameter(PermissionFragmentParameter(identMethod, permissionList, helpData), BUNDLE_PAGE)
    }

    private lateinit var pageData: PermissionFragmentParameter

    private val emmiReporter = EmmiCoreReporter

    private lateinit var viewBinding: PiFragmentPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NORMAL, android.R.style.ThemeOverlay_Material_ActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentPermissionBinding.inflate(inflater, container, false)
        pageData = checkNotNull(BUNDLE_PAGE.getParameter(arguments))

        removeUnnecessaryPermissions()
        pageData.permissionList.forEach { userPermission ->
            userPermission.reportingEvent?.let {event ->
                emmiReporter.send(event, identMethod = pageData.identMethod)
            }
        }

        showBackButton(viewBinding.root.findViewById(R.id.toolbar_actionbar), false) {  }

        pageData.helpData?.let {
            viewBinding.piPermissionHelp.visibility = View.VISIBLE
            viewBinding.piPermissionHelpTitle.text = it.title
            viewBinding.piPermissionHelpDescription.text = it.description
        }

        viewBinding.piPermissionsTitle.text = LocalizedStrings.getString("permissions_title")
        viewBinding.piPermissionButtonConfirm.text = LocalizedStrings.getString("default_btn_continue")
        viewBinding.piPermissionButtonConfirm.setOnClickListener {
            onContinueClicked()
        }
        updateUi()

        return viewBinding.root
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        val permissionList = pageData.permissionList.map { it.androidPermission }
        if (PermissionUtil.hasPermissions(requireContext(), permissionList)) {
            dismiss()
        }
    }

    private fun updateUi() {
        viewBinding.piPermissionContent.removeAllViews()
        pageData.permissionList.forEach { userPermission ->
            val view = PiPermissionItemBinding.inflate(requireActivity().layoutInflater, viewBinding.piPermissionContent, true)

            view.piPermissionIcon.setImageResource(userPermission.iconRes)
            view.piPermissionTitle.text = LocalizedStrings.getString(userPermission.titleKey)
            view.piPermissionDescription.text = LocalizedStrings.getString(userPermission.descriptionKey)

            if (PermissionUtil.hasPermissions(requireContext(), listOf(userPermission.androidPermission))) {
                view.piPermissionIcon.setImageResource(userPermission.iconRes)
                view.piAllowBtn.visibility = View.GONE
                view.piPermissionCheckmark.visibility = View.VISIBLE
            } else {
                if (PermissionUtil.arePermissionsDeniedPermanently(requireActivity(), listOf(userPermission.androidPermission))
                ) {
                    view.piAllowBtn.visibility = View.VISIBLE
                    view.piAllowBtn.text = LocalizedStrings.getString("permissions_btn_allow")
                    view.piPermissionCheckmark.visibility = View.GONE
                    view.piPermissionIcon.setImageResource(userPermission.iconResInactive)
                    view.piAllowBtn.setOnClickListener {
                        openSettings()
                    }
                }
            }
        }
    }

    private fun removeUnnecessaryPermissions(){
        pageData.permissionList.forEach { userPermission ->
            if (userPermission.requiredSinceSdkLevel > Build.VERSION.SDK_INT) pageData.permissionList.remove(userPermission)
        }
    }

    private fun onContinueClicked() {
        val permissionList = pageData.permissionList.map { it.androidPermission }.toMutableList()
        //only needed for logging purpose -> refactor later
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (pageData.identMethod == IdentMethod.VIDEO) {
                permissionList.add("android.permission.ACCESS_COARSE_LOCATION")
                permissionList.add("android.permission.READ_PHONE_STATE")
            }
        }
        if (PermissionUtil.hasPermissions(requireContext(), permissionList)) {
            dismiss()
        } else {
            if (permissionList.contains(UserPermission.NFC.androidPermission) && getNFCStatus(requireContext()) == NFCStatus.DISABLED) {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
            if (PermissionUtil.arePermissionsDeniedPermanently(requireActivity(), permissionList)) {
                openSettings()
            } else {
                requestPermissions(permissionList.toTypedArray(), 0)
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri: Uri = Uri.fromParts("package", requireActivity().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissions.mapNotNull { permission ->
            UserPermission.values().find { it.androidPermission == permission }
        }.forEach { permission ->
            permission.reportingEventResult?.let {
                if (PermissionUtil.hasPermissions(requireContext(), listOf(permission.androidPermission))) {
                    emmiReporter.send(it, pageData.identMethod, LogLevel.INFO, null, EventStatus.SUCCESS, null)
                } else {
                    emmiReporter.send(it, pageData.identMethod, LogLevel.ERROR, null, EventStatus.ERROR, null)
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (PermissionUtil.hasPermissions(requireContext(), pageData.permissionList.map { it.androidPermission })) {
            (activity as BaseModuleActivity).permissionsGranted()
        } else {
            requireActivity().finish()
        }
        super.onDismiss(dialog)
    }
}

