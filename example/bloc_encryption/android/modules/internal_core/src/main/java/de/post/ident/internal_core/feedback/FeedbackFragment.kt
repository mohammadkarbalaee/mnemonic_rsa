package de.post.ident.internal_core.feedback

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.BuildConfig
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.CustomerFeedbackDTO
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_core.util.ui.showKeyboard
import de.post.ident.internal_core.databinding.PiFeedbackFragmentBinding
import de.post.ident.internal_core.databinding.PiFeedbackRatingBarItemBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class RatingBarItem(private val binding: PiFeedbackRatingBarItemBinding, index: Int, onSelect: (Int) -> Unit) {
    var isSelected: Boolean = false
        set(value) {
            field = value
            val visibility = if (value) View.VISIBLE else View.INVISIBLE
            binding.highlightTop.visibility = visibility
            binding.highlightBottom.visibility = visibility
        }

    init {
        binding.marker.text = index.toString()
        binding.root.setOnClickListener { onSelect(index) }
        isSelected = false
    }
}

class FeedbackFragment : Fragment() {
    private val TAG = FeedbackFragment::class.java.simpleName

    companion object {
        @JsonClass(generateAdapter = true)
        data class FeedbackParameter(val customerFeedback: CustomerFeedbackDTO, val chatId: Int?)
        private val FEEDBACK_FRAGMENT_PARAMETER: BundleParameter<FeedbackParameter> = BundleParameter.moshi(CoreEmmiService.moshi, "FFP")
        fun newInstance(customerFeedback: CustomerFeedbackDTO, chatId: Int? = null): FeedbackFragment =
                FeedbackFragment().withParameter(FeedbackParameter(customerFeedback, chatId), FEEDBACK_FRAGMENT_PARAMETER)
    }

    private val emmiService = CoreEmmiService
    private val emmiReporter = EmmiCoreReporter
    private var isLoading = false
    private var isEnabled = false

    private lateinit var feedbackParameter: FeedbackParameter
    private lateinit var viewBinding: PiFeedbackFragmentBinding
    private lateinit var markers: List<RatingBarItem>
    private lateinit var submitButtonController: MaterialButtonLoadingController

    private val faces = listOf(
            R.drawable.pi_ic_slider_result_0,
            R.drawable.pi_ic_slider_result_1,
            R.drawable.pi_ic_slider_result_2,
            R.drawable.pi_ic_slider_result_3,
            R.drawable.pi_ic_slider_result_4,
            R.drawable.pi_ic_slider_result_5,
            R.drawable.pi_ic_slider_result_6,
            R.drawable.pi_ic_slider_result_7,
            R.drawable.pi_ic_slider_result_8,
            R.drawable.pi_ic_slider_result_9,
            R.drawable.pi_ic_slider_result_10
    )

    private lateinit var reviewManager: ReviewManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFeedbackFragmentBinding.inflate(inflater, container, false)
        markers = faces.mapIndexed { i, _ ->
            val markerBinding = PiFeedbackRatingBarItemBinding.inflate(inflater, viewBinding.ratingBar, true)
            RatingBarItem(markerBinding, i) {
                markers.forEachIndexed { index, ratingbarItem -> ratingbarItem.isSelected = index == it }
                viewBinding.ivFaceImage.setImageResource(faces[i])
                isEnabled = true
                updateUI()
            }
        }

        viewBinding.ivFaceImage.setImageResource(R.drawable.pi_ic_slider_result_default)

        feedbackParameter = checkNotNull(FEEDBACK_FRAGMENT_PARAMETER.getParameter(arguments))
        viewBinding.textFeedbackDescription.text = feedbackParameter.customerFeedback.description
        viewBinding.commentEdittext.hint = feedbackParameter.customerFeedback.commentHint

        viewBinding.tvCaption.text = LocalizedStrings.getString("recommend_caption_feedback")
        viewBinding.tvLeftBubbleText.text = LocalizedStrings.getString("recommend_unlikely")
        viewBinding.tvRightBubbleText.text = LocalizedStrings.getString("recommend_likely")
        viewBinding.ignoreFeedbackBtn.text = LocalizedStrings.getString("recommend_send_feedback_later")
        viewBinding.sendFeedbackBtn.text = LocalizedStrings.getString("recommend_send_feedback")

        viewBinding.ignoreFeedbackBtn.setOnClickListener {
            showKeyboard(requireActivity(), false)
            finishFeedback()
        }
        submitButtonController = MaterialButtonLoadingController(requireContext(), viewBinding.sendFeedbackBtn)
        viewBinding.sendFeedbackBtn.setOnClickListener {
            showKeyboard(requireActivity(), false)
            sendFeedback()
        }

        emmiReporter.send(
            logEvent = LogEvent.DISPLAY_RATING,
            attemptId = Commons.attemptId
        )
        Commons.attemptId = null

        updateUI()

        return viewBinding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        initReviewManager(context)
    }

    private fun updateUI() {
        viewBinding.commentsTextfield.isVisible = isEnabled
        viewBinding.commentsTextfield.isEnabled = isEnabled
        viewBinding.ignoreFeedbackBtn.isEnabled = isLoading.not()
        viewBinding.sendFeedbackBtn.isEnabled = isEnabled && isLoading.not()

        submitButtonController.loadingAnimation(isLoading, isEnabled)
    }

    private fun sendFeedback() {
        lifecycleScope.launch {
            isLoading = true
            updateUI()
            try {
                val rating = markers.indexOfFirst { it.isSelected }
                val comment = viewBinding.commentEdittext.text.toString()
                val path = feedbackParameter.customerFeedback.continueButton?.target
                if ( path != null) emmiService.sendCustomerFeedback(rating, comment, path, feedbackParameter.chatId)
                isEnabled = false
            } catch (err: Throwable) {
                log(err)
                isEnabled = true
            } finally {
                isLoading = false
                ensureActive()
                if (CoreConfig.isSdk.not()) {
                    startReviewFlow()
                } else {
                    finishFeedback()
                }
                updateUI()
            }
        }
    }

    private fun initReviewManager(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG build; init fake review manager")
            reviewManager = FakeReviewManager(context)
        } else {
            Log.d(TAG, "init review manager")
            reviewManager = ReviewManagerFactory.create(context)
        }
    }

    private fun startReviewFlow() = reviewManager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val reviewInfo: ReviewInfo = task.result
            reviewManager.launchReviewFlow(requireActivity(), reviewInfo).addOnCompleteListener {
                Log.d(TAG, "review flow complete")
                finishFeedback()
            }
            Log.d(TAG, "in app review flow started")
        } else {
            finishFeedback()
            Log.d(TAG, "in app review failed to start")
        }
    }

    private fun finishFeedback() {
        (requireActivity() as BaseModuleActivity).finishSdkWithSuccess()
    }
}
