package jenkins.advancedqueue.priority.strategyitems;

import java.util.List;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Queue;

public interface IStrategyItem {
    Queue.Task getTask();
    <T extends Action> List<T> getActions(Class<T> type);
    List<Cause> getCauses();
}