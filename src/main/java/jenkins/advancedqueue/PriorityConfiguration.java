/*
 * The MIT License
 *
 * Copyright (c) 2013, Magnus Sandberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.advancedqueue;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Plugin;
import hudson.matrix.MatrixConfiguration;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.ViewGroup;
import hudson.model.View;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import javax.servlet.ServletException;

import jenkins.advancedqueue.jobinclusion.JobInclusionStrategy;
import jenkins.advancedqueue.priority.PriorityStrategy;
import jenkins.advancedqueue.priority.strategyitems.IStrategyItem;
import jenkins.advancedqueue.priority.strategyitems.PlaceholderItemStrategyItem;
import jenkins.advancedqueue.priority.strategyitems.QueueItemStrategyItem;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Magnus Sandberg
 * @since 2.0
 */
@Extension
public class PriorityConfiguration extends Descriptor<PriorityConfiguration> implements RootAction, Describable<PriorityConfiguration> {

	private final static Logger LOGGER = Logger.getLogger(PriorityConfiguration.class.getName());

	transient private Map<Integer, JobGroup> id2jobGroup;
	transient private PriorityConfigurationMatrixHelper priorityConfigurationMatrixHelper;
	transient private PriorityConfigurationPlaceholderTaskHelper placeholderTaskHelper = new PriorityConfigurationPlaceholderTaskHelper();
	private List<JobGroup> jobGroups;

	public PriorityConfiguration() {
		super(PriorityConfiguration.class);
		jobGroups = new LinkedList<JobGroup>();
		load();
		//
		Collections.sort(jobGroups, new Comparator<JobGroup>() {
			public int compare(JobGroup o1, JobGroup o2) {
				return o1.getId() - o2.getId();
			}
		});
		//
		id2jobGroup = new HashMap<Integer, JobGroup>();
		for (JobGroup jobGroup : jobGroups) {
			id2jobGroup.put(jobGroup.getId(), jobGroup);
			Collections.sort(jobGroup.getPriorityStrategies(), new Comparator<JobGroup.PriorityStrategyHolder>() {
				public int compare(JobGroup.PriorityStrategyHolder o1, JobGroup.PriorityStrategyHolder o2) {
					return o1.getId() - o2.getId();
				}
			});
		}
		//
		Plugin plugin = Jenkins.getInstance().getPlugin("matrix-project");
		if(plugin == null || !plugin.getWrapper().isEnabled()){
			priorityConfigurationMatrixHelper = null;
		} else {
			priorityConfigurationMatrixHelper = new PriorityConfigurationMatrixHelper();
		}
	}

	public String getIconFileName() {
		if (!checkActive()) {
			return null;
		}
		return "/plugin/PrioritySorter/advqueue.png";
	}

	@Override
	public String getDisplayName() {
		return "Job Priorities";
	}

	public String getUrlName() {
		if (!checkActive()) {
			return null;
		}
		return "advanced-build-queue";
	}

	private boolean checkActive() {
		PrioritySorterConfiguration configuration = PrioritySorterConfiguration.get();
		if (configuration.getOnlyAdminsMayEditPriorityConfiguration()) {
			return Jenkins.getInstance().getACL().hasPermission(Jenkins.ADMINISTER);
		}
		return true;
	}

	public List<JobGroup> getJobGroups() {
		return jobGroups;
	}

	public JobGroup getJobGroup(int id) {
		return id2jobGroup.get(id);
	}

	public ExtensionList<Descriptor<PriorityStrategy>> getPriorityStrategyDescriptors() {
		return PriorityStrategy.all();
	}
	
	public DescriptorExtensionList<JobInclusionStrategy, Descriptor<JobInclusionStrategy>> getJobInclusionStrategyDescriptors() {
		return JobInclusionStrategy.all();
	}


	public ListBoxModel getPriorities() {
		ListBoxModel items = PrioritySorterConfiguration.get().doGetPriorityItems();
		return items;
	}

	public void doPriorityConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		jobGroups = new LinkedList<JobGroup>();
		id2jobGroup = new HashMap<Integer, JobGroup>();
		//
		String parameter = req.getParameter("json");
		JSONObject jobGroupsObject = JSONObject.fromObject(parameter);
		JSONArray jsonArray = JSONArray.fromObject(jobGroupsObject.get("jobGroup"));
		int id = 0;
		for (Object object : jsonArray) {
			JSONObject jobGroupObject = JSONObject.fromObject(object);
			if (jobGroupObject.isEmpty()) {
				break;
			}
			JobGroup jobGroup = JobGroup.newInstance(req, jobGroupObject, id++);
			jobGroups.add(jobGroup);
			id2jobGroup.put(jobGroup.getId(), jobGroup);
		}
		save();
		rsp.sendRedirect(Jenkins.getInstance().getRootUrl());
	}

	public Descriptor<PriorityConfiguration> getDescriptor() {
		return this;
	}

	public FormValidation doCheckJobPattern(@QueryParameter String value) throws IOException, ServletException {
		if (value.length() > 0) {
			try {
				Pattern.compile(value);
			} catch (PatternSyntaxException e) {
				return FormValidation.warning("The expression is not valid, please enter a valid expression.");
			}
		}
		return FormValidation.ok();
	}

	public PriorityConfigurationCallback getPriority(Queue.Item item, PriorityConfigurationCallback priorityCallback) {
		SecurityContext saveCtx = ACL.impersonate(ACL.SYSTEM);
		try {
			return getPriorityInternal(item, priorityCallback);
		} finally {
			SecurityContextHolder.setContext(saveCtx);
		}
	}

	private PriorityConfigurationCallback getPriorityInternal(Queue.Item i, PriorityConfigurationCallback priorityCallback) {
		IStrategyItem item = new QueueItemStrategyItem(i);
		if (placeholderTaskHelper.isPlaceholderTask(i.task)) {
			ExecutorStepExecution.PlaceholderTask placeholderTask = (ExecutorStepExecution.PlaceholderTask) i.task;
			Run run = placeholderTask.run();
			if (run != null) {
				item = new PlaceholderItemStrategyItem(placeholderTask);
			} else {
				return placeholderTaskHelper.getPriority(placeholderTask, priorityCallback);
			}
		} 
		if (!(item.getTask() instanceof Job)) {
			// Not a job generally this mean that this is a lightweight task so
			// priority doesn't really matter - returning default priority
			priorityCallback.addDecisionLog(0, "Queue.Item is not a Job - Assigning global default priority");
			return priorityCallback.setPrioritySelection(PrioritySorterConfiguration.get().getStrategy().getDefaultPriority());
		}

		Job<?, ?> job = (Job<?, ?>) item.getTask();
		
		if (priorityConfigurationMatrixHelper != null && priorityConfigurationMatrixHelper.isMatrixConfiguration(job)) {
			return priorityConfigurationMatrixHelper.getPriority((MatrixConfiguration) job, priorityCallback);
		}

		//
		JobGroup jobGroup = getJobGroup(priorityCallback, job);
		if (jobGroup != null) {
			return getPriorityForJobGroup(priorityCallback, jobGroup, item);
		}
		//
		priorityCallback.addDecisionLog(0, "Assigning global default priority");
		return priorityCallback.setPrioritySelection(PrioritySorterConfiguration.get().getStrategy().getDefaultPriority());
	}

        @CheckForNull
	public JobGroup getJobGroup(@Nonnull PriorityConfigurationCallback priorityCallback, @Nonnull Job<?, ?> job) {
		if (!(job instanceof TopLevelItem)) {
			priorityCallback.addDecisionLog(0, "Job is not a TopLevelItem [" + job.getClass().getName() + "] ...");
			return null;
		}
		for (JobGroup jobGroup : jobGroups) {
			priorityCallback.addDecisionLog(0, "Evaluating JobGroup [" + jobGroup.getId() + "] ...");
			if (jobGroup.getJobGroupStrategy().contains(priorityCallback, job)) {
				return jobGroup;
			}
		}
		return null;
	}
	
	private boolean isJobInView(Job<?, ?> job, View view) {
		if(view instanceof ViewGroup) {
			return isJobInViewGroup(job, (ViewGroup) view);
		} else {
			return view.contains((TopLevelItem) job); 
		}
	}
	
	private boolean isJobInViewGroup(Job<?, ?> job, ViewGroup viewGroup) {
		Collection<View> views = viewGroup.getViews();
		for (View view : views) {
			if(isJobInView(job, view)) {
				return true;
			}
		}
		return false;
	}
	
	private PriorityConfigurationCallback getPriorityForJobGroup(PriorityConfigurationCallback priorityCallback, JobGroup jobGroup, IStrategyItem item) {
		int priority = jobGroup.getPriority();
		PriorityStrategy reason = null;
		if (jobGroup.isUsePriorityStrategies()) {
			priorityCallback.addDecisionLog(2, "Evaluating strategies ...");
			List<JobGroup.PriorityStrategyHolder> priorityStrategies = jobGroup.getPriorityStrategies();
			for (JobGroup.PriorityStrategyHolder priorityStrategy : priorityStrategies) {
				PriorityStrategy strategy = priorityStrategy.getPriorityStrategy();
				priorityCallback.addDecisionLog(3, "Evaluating strategy [" + strategy.getDescriptor().getDisplayName() + "] ...");
				if (strategy.isApplicable(item)) {
					priorityCallback.addDecisionLog(4, "Strategy is applicable");
					int foundPriority = strategy.getPriority(item);
					if (foundPriority > 0 && foundPriority <= PrioritySorterConfiguration.get().getStrategy().getNumberOfPriorities()) {
						priority = foundPriority;
						reason = strategy;
						break;
					}
				}
			}
		}
		if (reason == null) {
			priorityCallback.addDecisionLog(2, "No applicable strategy - Using JobGroup default");
		}
		if (priority == PriorityCalculationsUtil.getUseDefaultPriorityPriority()) {
			priority = PrioritySorterConfiguration.get().getStrategy().getDefaultPriority();
		}
		return priorityCallback.setPrioritySelection(priority, jobGroup.getId(), reason);
	}

	static public PriorityConfiguration get() {
		return (PriorityConfiguration) Jenkins.getInstance().getDescriptor(PriorityConfiguration.class);
	}

}
