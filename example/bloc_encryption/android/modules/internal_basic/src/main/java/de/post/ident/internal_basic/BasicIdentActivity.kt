package de.post.ident.internal_basic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.rest.IdentMethodDTO
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.UserPermission
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.*
import de.post.ident.internal_core.util.BasicCaseManager
import kotlinx.coroutines.launch

class BasicIdentActivity : BaseModuleActivity() {
    companion object {
        private val OFFLINE_COUPON_PARAMETER: ExtraParameter<Boolean> = ExtraParameter.moshi(
            CoreEmmiService.moshi, "OFFLINE_COUPON")

        fun startOfflineCoupon(context: Context, data: CaseResponseDTO) {
            val intent = Intent(context, BasicIdentActivity::class.java).apply {
                putParameter(CASE_RESPONSE_PARAMETER, data)
                putParameter(OFFLINE_COUPON_PARAMETER, true)
            }
            context.startActivity(intent)
        }
    }

    private val caseData by lazy {
        val caseResponse = getCaseResponse()
        BasicCaseManager.setCase(caseResponse.caseId, caseResponse)
        caseResponse
    }

    override val moduleMetaData: ModuleMetaData by lazy {
        val permissionList = mutableListOf<UserPermission>()
        val drawableList = arrayListOf(R.drawable.pi_pd_get_coupon, R.drawable.pi_pd_goto_shop, R.drawable.pi_pd_scan_code)
        val pageDataList: ArrayList<ProcessDescriptionFragment.PageData> = arrayListOf()
        val showProcessDescription = OFFLINE_COUPON_PARAMETER.getExtra(intent)?.not() ?: true
        if (showProcessDescription) {
            val titleList = checkNotNull(getCaseResponse().modules.processDescription?.subtitle)
            pageDataList.addAll(
                titleList.mapIndexedNotNull { index, title ->
                    if (index < drawableList.size) {
                        ProcessDescriptionFragment.PageData(title, drawableList[index])
                    } else {
                        null
                    }
                }
            )
        }

        ModuleMetaData(
                PermissionFragmentParameter(IdentMethod.BASIC, permissionList, null),
                if (showProcessDescription) {
                    ProcessDescriptionFragment.ProcessDescriptionData(getProcessDescriptionData(), pageDataList, IdentMethodDTO.BASIC)
                } else {
                    null
                }
        )
    }

    override fun permissionsGranted() {
        // placeholder for later integration of coupon or something that requires permissions
        lifecycleScope.launch { sendTermsAccepted() }
        showFragment(BasicIdentFragment.newInstance(caseData))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OFFLINE_COUPON_PARAMETER.getExtra(intent)?.let {
            if (it) {
                hideContinueButtons()
                setupHomeButton()
                showFragment(BasicIdentFragment.newInstanceOfflineCoupon(caseData))
            }
        }
        initContinueButton(viewBinding.btnContinueStandard) { showFragment(BasicIdentFragment.newInstance(caseData)) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupHomeButton() {
        viewBinding.toolbar.toolbarActionbar.title = ""
        viewBinding.toolbar.toolbarActionbar.subtitle = ""
        setSupportActionBar(viewBinding.toolbar.toolbarActionbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}