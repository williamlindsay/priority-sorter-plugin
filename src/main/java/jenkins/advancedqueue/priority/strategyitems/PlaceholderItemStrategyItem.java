package jenkins.advancedqueue.priority.strategyitems;

import java.util.List;

import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Queue;

public class PlaceholderItemStrategyItem implements IStrategyItem {
    private ExecutorStepExecution.PlaceholderTask task;

    public PlaceholderItemStrategyItem(ExecutorStepExecution.PlaceholderTask task) {
        this.task = task;
    }

    public Queue.Task getTask() {
        return this.task.getOwnerTask();
    }

    public <T extends Action> List<T> getActions(Class<T> type) {
        return this.task.run().getActions(type);
    }
    
    public List<Cause> getCauses() {
        return this.task.run().getCauses();
    }
}