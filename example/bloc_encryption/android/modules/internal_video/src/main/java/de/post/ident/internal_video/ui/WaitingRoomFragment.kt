package de.post.ident.internal_video.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.showChoiceDialog
import de.post.ident.internal_video.NovomindEvent
import de.post.ident.internal_video.databinding.PiFragmentWaitingRoomBinding
import java.util.concurrent.TimeUnit


class WaitingRoomFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): WaitingRoomFragment = WaitingRoomFragment()

        private const val ANIMATION_DURATION = 1000L //ms
        private const val WAITING_LINE_LONG_THRESHOLD = 15 //min
    }

    private lateinit var viewBinding: PiFragmentWaitingRoomBinding
    private var expectedWaitingTimeMinutes = -1
    private var waitedMinutes = 0L
    private var tStart = 0L
    private var tNow = 0L
    private var dialogWasShown = false

    private val bottomSheetHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val bottomSheetHandlerRunnable: Runnable by lazy { Runnable { (context as VideoIdentActivity).showWaitingQueueInfo() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoManager.novomindEventBus.subscribe(this) {
            when (it) {
                is NovomindEvent.WaitingTime -> {
                    calculateTimeWaited()
                    updateWaitingTime(it.timeSeconds)
                }
                NovomindEvent.NoAgentAvailable -> showAlertDialog(
                    requireContext(), LocalizedStrings.getString("dialog_no_agent_available")) {
                    videoManager.endCall()
                }
                NovomindEvent.TimeoutInQueue -> showAlertDialog(
                    requireContext(), LocalizedStrings.getString("dialog_timeout_agent")) {
                    videoManager.endCall()
                }
                else -> {}
            }
        }
    }

    private fun calculateTimeWaited() {
        tNow = System.nanoTime()
        waitedMinutes = TimeUnit.MINUTES.convert((tNow - tStart), TimeUnit.NANOSECONDS)
        log("$waitedMinutes minutes waited")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = PiFragmentWaitingRoomBinding.inflate(inflater, container, false)

        viewBinding.waitingTimeValue.setFactory {
            TextView(requireContext()).apply { setTypeface(typeface, Typeface.BOLD) }
        }
        viewBinding.waitingTimeEstimate.text = LocalizedStrings.getString("waiting_line_estimated_duration")
        viewBinding.waitingLineTitle.text = LocalizedStrings.getString("waiting_line_connection_gets_established")

        emmiReporter.send(LogEvent.VC_WAIT)

        viewBinding.waitingLineText.text = videoManager.waitingTimeInfo

        updateWaitingTime(-1)
        tStart = System.nanoTime()

        return viewBinding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetHandler.postDelayed(bottomSheetHandlerRunnable, 2000)
    }

    override fun onDetach() {
        super.onDetach()
        bottomSheetHandler.removeCallbacks(bottomSheetHandlerRunnable)
    }

    private fun updateWaitingTime(waitingTimeSeconds: Int) {
        if (waitingTimeSeconds == 0) {
            viewBinding.waitingLineTitle.visibility = View.VISIBLE
            viewBinding.waitingLineTime.animate().setDuration(ANIMATION_DURATION).alpha(0.0f)
        } else if (waitingTimeSeconds > 0) {
            viewBinding.waitingLineTitle.visibility = View.VISIBLE
            viewBinding.waitingLineTime.animate().setDuration(ANIMATION_DURATION).alpha(1.0f)

            expectedWaitingTimeMinutes = videoManager.convertWaitingTimeToMinutes(waitingTimeSeconds)
            if (expectedWaitingTimeMinutes >= 10 && waitedMinutes >= 5) {
                viewBinding.buttonToMethodSelection.text = LocalizedStrings.getString("default_btn_back_to_methodselection")
                viewBinding.buttonToMethodSelection.isEnabled = true
                viewBinding.buttonToMethodSelection.isVisible = true
                viewBinding.buttonToMethodSelection.setOnClickListener {
                    showChoiceDialog(
                        context = requireContext(),
                        title = LocalizedStrings.getString("process_cancel_dialog_title"),
                        msg = LocalizedStrings.getString("dialog_cancel_call_message"),
                        positiveButton = LocalizedStrings.getString("default_btn_yes"),
                        onPositive = {
                            emmiReporter.send(LogEvent.VC_WAITING_LINE_ABORT)
                            videoManager.endCall(true)
                            activity?.finish()
                        },
                        negativeButton = LocalizedStrings.getString("default_btn_no")
                    )
                }
            }

            viewBinding.waitingTimeValue.setText(LocalizedStrings.getQuantityString("waiting_line_time_unit", expectedWaitingTimeMinutes, expectedWaitingTimeMinutes))
        }

        if (dialogWasShown.not() && expectedWaitingTimeMinutes >= WAITING_LINE_LONG_THRESHOLD) {
            showCallcenterFullDialog()
            dialogWasShown = true
        }
    }

    private fun showCallcenterFullDialog() {
        showChoiceDialog(
                requireActivity(),
                LocalizedStrings.getString("dialog_callcenter_full_title"),
                LocalizedStrings.getString("dialog_callcenter_full", WAITING_LINE_LONG_THRESHOLD),
                LocalizedStrings.getString("default_btn_quit"),
                LocalizedStrings.getString("dialog_cancel_call_cancel_button_text"),
                false,
                { activity?.finish() }
        )

        val eventContext = mapOf(
                "timeToDisplayInMinutes" to WAITING_LINE_LONG_THRESHOLD.toString(),
                "timeInMinutes" to expectedWaitingTimeMinutes.toString())
        emmiReporter.send(LogEvent.IH_SERVICECENTER_BUSY, eventContext = eventContext)
    }
}