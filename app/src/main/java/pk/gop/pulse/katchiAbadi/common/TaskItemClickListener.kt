package pk.gop.pulse.katchiAbadi.common

import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity

interface TaskItemClickListener {
    fun onUploadTaskClicked(task: TaskEntity)
    fun onDeleteTaskClicked(task: TaskEntity)
    fun onViewTaskClicked(task: TaskEntity)
}