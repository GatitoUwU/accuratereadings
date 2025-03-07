package com.sparkedhost.accuratereadings.config;

import com.sparkedhost.accuratereadings.Main;
import com.sparkedhost.accuratereadings.exceptions.AlreadyExistsException;
import com.sparkedhost.accuratereadings.exceptions.InvalidPowerActionException;
import com.sparkedhost.accuratereadings.exceptions.InvalidTaskTypeException;
import com.sparkedhost.accuratereadings.managers.TaskManager;
import com.sparkedhost.accuratereadings.tasks.Task;
import com.sparkedhost.accuratereadings.tasks.TaskType;
import com.sparkedhost.accuratereadings.tasks.TaskValidator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

public class TaskSettings {
    FileConfiguration config = Main.getInstance().getConfig();
    ConfigurationSection tasksSection = config.getConfigurationSection("tasks");

    public void load() {
        TaskManager.getInst().clear();

        Set<String> tasksSet = tasksSection.getKeys(false);

        if (tasksSet.size() == 0) {
            Main.getInstance().getLogger().info("No tasks found.");
            return;
        }

        int activeTasks = 0;
        for (String taskEntry : tasksSet) {
            try {
                TaskType taskType = TaskValidator.validateTaskType(tasksSection.getString(taskEntry + ".type").toUpperCase());
                boolean isActive = tasksSection.getBoolean(taskEntry + ".active", true);
                Task.TaskBuilder taskBuilder = Task.builder()
                        .name(taskEntry)
                        .active(isActive)
                        .type(taskType)
                        .thresholdValue(tasksSection.getString(taskEntry + ".threshold"));

                if (isActive) activeTasks++;

                switch (taskType) {
                    case POWER:
                        taskBuilder.payload(TaskValidator.validatePowerAction(tasksSection.getString(taskEntry + ".payload")));
                        break;
                    case BROADCAST:
                    case COMMAND:
                        taskBuilder.payload(tasksSection.getString(taskEntry + ".payload"));
                        break;
                }

                TaskManager.getInst().addTask(taskBuilder.build());
            } catch (InvalidTaskTypeException e) {
                Main.getInstance().getLogger().warning("Invalid task type for task '" + taskEntry + "', ignoring task.");
            } catch (InvalidPowerActionException e) {
                Main.getInstance().getLogger().warning("Invalid power action for task '" + taskEntry + "', ignoring task.");
            } catch (AlreadyExistsException e) {
                Main.getInstance().getLogger().warning("Task '" + taskEntry + "' is duplicated, ignoring.");
            }
        }

        Main.getInstance().getLogger().info("Successfully loaded " + TaskManager.getInst().getTasks().size() + " tasks, " + activeTasks + " active.");
    }
}
