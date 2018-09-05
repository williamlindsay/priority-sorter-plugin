package jenkins.advancedqueue.priority.strategyitems;

import java.util.List;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Queue;

public class QueueItemStrategyItem implements IStrategyItem {
    private Queue.Item item;

    public QueueItemStrategyItem(Queue.Item item) {
        this.item = item;
    }

    public Queue.Task getTask() {
        return this.item.task;
    }

    public <T extends Action> List<T> getActions(Class<T> type) {
        return this.item.getActions(type);
    }
    
    public List<Cause> getCauses() {
        return this.item.getCauses();
    }
}