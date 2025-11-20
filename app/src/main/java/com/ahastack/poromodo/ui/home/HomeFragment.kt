package com.ahastack.poromodo.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahastack.poromodo.MainViewModel
import com.ahastack.poromodo.R
import com.ahastack.poromodo.databinding.FragmentHomeBinding
import com.ahastack.poromodo.model.PomodoroPhase
import com.ahastack.poromodo.model.Task
import com.ahastack.poromodo.ui.home.TaskRecyclerViewAdapter.MenuAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), AddTaskDialogFragment.OnTaskAddedListener,
    UpdateTaskDialogFragment.OnTaskUpdatedListener {

    private val vm: MainViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null
    private lateinit var taskAdapter: TaskRecyclerViewAdapter

    private val binding get() = _binding!!
    fun formatTime(seconds: Int): String {
        val minutes = seconds / 60 // Integer division to get minutes
        val remainingSeconds = seconds % 60 // Modulo to get remaining seconds
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressBar: ProgressBar = binding.statusProgressBar
        val button: Button = binding.podomoroButton
        val timeLeftTv: TextView = binding.tvTimeLeft
        val phaseToggleGroup = binding.phaseToggleGroup
        val taskAddBtn: Button = binding.btnTaskAdd
        phaseToggleGroup.check(R.id.phase_toggle_group)
        phaseToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.phase_pomodoro -> vm.setPhase(PomodoroPhase.PODOMORO)
                    R.id.phase_short_break -> vm.setPhase(PomodoroPhase.BREAK)
                    R.id.phase_long_break -> vm.setPhase(PomodoroPhase.LONG_BREAK)
                }
            }
        }

        taskAdapter = TaskRecyclerViewAdapter(vm) { task, action ->
            Log.d("Menu Action", "onTaskHandle: $task $action")
            when (action) {
                MenuAction.EDIT -> {
                    val dialog = UpdateTaskDialogFragment(task)
                    dialog.setOnTaskUpdatedListener(this)
                    dialog.show(childFragmentManager, "EditTaskDialog")
                }

                MenuAction.DELETE -> {
                    vm.delTask(task)
                }

                MenuAction.MARK_COMPLETE -> {
                    vm.completeTask(task)
                }

                MenuAction.FOCUS -> {
                    vm.focusTask(task)
                }
            }
        }
        binding.rclvTaskList.layoutManager = LinearLayoutManager(requireContext())
        binding.rclvTaskList.adapter = taskAdapter

        // Add Task Button
        taskAddBtn.setOnClickListener {
            val dialog = AddTaskDialogFragment()
            Log.d("CHUNGUS", " DIALOG CREATE")
            dialog.setOnTaskAddedListener(this)
            dialog.show(childFragmentManager, "AddTaskDialog")
        }

        button.setOnClickListener {
            if (!vm.isRunning.value) {
                vm.startTimer()
            } else {
                vm.pauseTimer()
            }
        }

        // Load tasks from Room database
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.timeRemaining.collectLatest {
                        timeLeftTv.text = formatTime(it)
                    }
                }

                launch {
                    vm.progress.collectLatest {
                        progressBar.progress = it
                    }
                }
                launch {
                    vm.tasks().collect {
                        taskAdapter.submitList(it)
                    }
                }

                launch {
                    vm.isRunning.collectLatest {
                        if (it) {
                            button.text = "PAUSE"
                            binding.statusLottie.playAnimation()
                        } else {
                            button.text = "START"
                            binding.statusLottie.pauseAnimation()
                        }
                    }
                }
                launch {
                    vm.timerInstance.collectLatest {
                        when (it.currentPhase) {
                            PomodoroPhase.PODOMORO -> {
                                phaseToggleGroup.check(R.id.phase_pomodoro)
                                binding.statusLottie.setAnimation(R.raw.tomato_lottie)
                            }

                            PomodoroPhase.BREAK -> {
                                phaseToggleGroup.check(R.id.phase_short_break)
                                binding.statusLottie.setAnimation(R.raw.cafe_break)
                            }

                            PomodoroPhase.LONG_BREAK -> {
                                phaseToggleGroup.check(R.id.phase_long_break)
                                binding.statusLottie.setAnimation(R.raw.long_break)
                            }
                        }
                    }
                }

                launch {
                    vm.focusedTask.collectLatest {
                        val focusedTaskView = binding.focusedTaskView
                        when (it) {
                            null -> {
                                focusedTaskView.tvTaskTitle.text = "No task is focused yet"
                                focusedTaskView.tvTaskDescription.visibility = View.GONE
                                focusedTaskView.tvTaskProgress.visibility = View.GONE
                                focusedTaskView.ivProgressIcon.visibility = View.INVISIBLE
                                focusedTaskView.btnTaskMenu.visibility = View.GONE
                            }

                            else -> {
                                focusedTaskView.tvTaskTitle.text = it.title
                                focusedTaskView.tvTaskDescription.text = it.description
                                focusedTaskView.tvTaskDescription.visibility = View.VISIBLE
                                focusedTaskView.tvTaskProgress.visibility = View.VISIBLE
                                focusedTaskView.ivProgressIcon.visibility = View.VISIBLE
                                focusedTaskView.btnTaskMenu.visibility = View.VISIBLE

                                val isCompleted =
                                    it.numOfPodomoroSpend == it.numOfPodomoroToComplete
                                val iconResource =
                                    if (isCompleted) R.drawable.ic_task_done else R.drawable.ic_task_progress
                                focusedTaskView.ivProgressIcon.setImageResource(iconResource)
                                focusedTaskView.tvTaskProgress.text =
                                    "${it.numOfPodomoroSpend}/${it.numOfPodomoroToComplete}"

                                focusedTaskView.btnTaskMenu.setOnClickListener { view ->
                                    val popupMenu = PopupMenu(view.context, view)
                                    popupMenu.menu.add(0, 0, 0, "Edit")
                                    popupMenu.menu.add(0, 1, 1, "Delete")
                                    popupMenu.menu.add(0, 2, 2, "Mark Complete")
                                    popupMenu.menu.add(0, 3, 3, "Unfocus")

                                    popupMenu.setOnMenuItemClickListener { menuItem ->
                                        when (menuItem.itemId) {
                                            0 -> {
                                                val dialog = UpdateTaskDialogFragment(it)
                                                dialog.setOnTaskUpdatedListener(this@HomeFragment)
                                                dialog.show(childFragmentManager, "EditTaskDialog")
                                            }

                                            1 -> {
                                                vm.delTask(it)
                                            }

                                            2 -> {
                                                vm.completeTask(it)
                                            }

                                            3 -> {
                                                vm.unFocusTask()
                                            }
                                        }
                                        true
                                    }
                                    popupMenu.show()
                                }
                            }
                        }
                    }
                }

            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onTaskAdded(task: Task) {
        Log.d("CHUNGUS", "onTaskAdded: $task")
        CoroutineScope(Dispatchers.IO).launch {
            vm.upsertTask(task)
        }
    }

    override fun onTaskUpdated(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            vm.updateTask(task)
        }
    }
}