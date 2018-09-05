/*
 * The MIT License
 *
 * Copyright (c) 2014, Magnus Sandberg
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
package jenkins.advancedqueue.priority.strategy;

import hudson.Extension;
import hudson.model.Job;
import javax.annotation.CheckForNull;
import jenkins.advancedqueue.PrioritySorterConfiguration;
import jenkins.advancedqueue.priority.strategyitems.IStrategyItem;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Magnus Sandberg
 * @since 3.0
 */
public class JobPropertyStrategy extends AbstractDynamicPriorityStrategy {

	@Extension
	public static class UserIdCauseStrategyDescriptor extends AbstractDynamicPriorityStrategyDescriptor {

		public UserIdCauseStrategyDescriptor() {
			super("Take the priority from Property on the Job");
		}

	}

	@DataBoundConstructor
	public JobPropertyStrategy() {
	}
	
	@CheckForNull
	private Integer getPriorityInternal(IStrategyItem item) {
		if(item.getTask() instanceof Job<?, ?>) {
			Job<?, ?> job = (Job<?, ?>) item.getTask();
			PriorityJobProperty priorityProperty = job.getProperty(PriorityJobProperty.class);
			if (priorityProperty != null && priorityProperty.getUseJobPriority()) {
				return priorityProperty.priority;
			}
		} 
		return null;
	}

	@Override
	public boolean isApplicable(IStrategyItem item) {
		return getPriorityInternal(item) != null;
	}

	@Override
	public int getPriority(IStrategyItem item) {
		final Integer p = getPriorityInternal(item);
		return p != null ? p : PrioritySorterConfiguration.get().getStrategy().getDefaultPriority();
	}

}
