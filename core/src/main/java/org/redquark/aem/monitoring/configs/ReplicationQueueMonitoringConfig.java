package org.redquark.aem.monitoring.configs;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * @author Anirudh Sharma
 *
 */
@ObjectClassDefinition(
		name = "Replication Queue Monitoring", 
		description = "This configuration is used to monitor the replication queue"
	)
public @interface ReplicationQueueMonitoringConfig {
	
	@AttributeDefinition(
			name = "Scheduler Name",
			description = "Name of the scheduler",
			type = AttributeType.STRING
		)
	public String getSchedulerName();
	
	@AttributeDefinition(
			name = "Agent Name", 
			description = "Name of the replication agent which you wish to monitor", 
			type = AttributeType.STRING
		)
	public String getAgentName();
	
	@AttributeDefinition(
			name = "Cron Expression",
			description = "Cron expression to determine when this scheduler will run",
			type = AttributeType.STRING
		)
	public String getCronExpression() default "0 * * * * ?";
	
	@AttributeDefinition(
			name = "Enabled",
			description = "If true, the scheduler will be enabled",
			type = AttributeType.BOOLEAN
		)
	public boolean isEnabled();
}
