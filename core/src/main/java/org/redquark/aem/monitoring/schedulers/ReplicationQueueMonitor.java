package org.redquark.aem.monitoring.schedulers;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.redquark.aem.monitoring.configs.ReplicationQueueMonitoringConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.mailer.MessageGatewayService;

/**
 * @author Anirudh Sharma
 *
 */
@Component(immediate = true, service = ReplicationQueueMonitor.class)
@Designate(ocd = ReplicationQueueMonitoringConfig.class, factory = true)
public class ReplicationQueueMonitor implements Runnable {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private String agentName;
	private boolean clearQueue;

	@Reference
	private Scheduler scheduler;

	@Reference
	private MessageGatewayService messageGatewayService;

	@Activate
	protected void activate(ReplicationQueueMonitoringConfig config) {
		agentName = config.getAgentName();
		clearQueue = config.isClearQueue();
	}

	@Modified
	protected void modified(ReplicationQueueMonitoringConfig config) {

		// Removing the scheduler
		removeScheduler(config);

		// Adding the scheduler
		addScheduler(config);
	}

	private void addScheduler(ReplicationQueueMonitoringConfig config) {
		if (config.isEnabled()) {
			ScheduleOptions options = scheduler.EXPR(config.getCronExpression());
			options.name(config.getSchedulerName());
			options.canRunConcurrently(false);
			scheduler.schedule(this, options);
			log.info("Scheduler is added: {}", config.getSchedulerName());
		} else {
			log.info("Scheduler is disabled: {}", config.getSchedulerName());
			removeScheduler(config);
		}
	}

	@Deactivate
	protected void deactivate(ReplicationQueueMonitoringConfig config) {
		log.info("Scheduler is removed: {}", config.getSchedulerName());
		removeScheduler(config);
	}

	private void removeScheduler(ReplicationQueueMonitoringConfig config) {
		log.info("Removing scheduler: {}", config.getSchedulerName());
		scheduler.unschedule(config.getSchedulerName());
	}

	@Override
	public void run() {
		log.info("Starting monitoring");
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			Set<ObjectName> names;
			names = server.queryNames(new ObjectName("com.adobe.granite.replication:type=agent,*"), null);
			Iterator<ObjectName> replicationMBean = names.iterator();

			while (replicationMBean.hasNext()) {
				ObjectName replicationAgent = replicationMBean.next();
				if (agentName.equalsIgnoreCase((String) server.getAttribute(replicationAgent, "Id"))) {
					boolean valid = (boolean) server.getAttribute(replicationAgent, "Valid");
					boolean enabled = (boolean) server.getAttribute(replicationAgent, "Enabled");
					boolean queuePaused = (boolean) server.getAttribute(replicationAgent, "QueuePaused");
					boolean queueBlocked = (boolean) server.getAttribute(replicationAgent, "QueueBlocked");

					boolean precondition = valid && enabled && !queuePaused && !queueBlocked;

					if (precondition) {
						log.info("Everything is working FINE. Queue last checked: {}", LocalDateTime.now());
					} else {
						log.error("Queue is NOT able to process items. Queue last checked: {}", LocalDateTime.now());
						sendMessageToSlack();
						if (clearQueue) {
							log.info("Clearing queue automatically");
							server.invoke(replicationAgent, "queueClear", new Object[] { null },
									new String[] { String.class.getName() });
							if ((Integer) server.getAttribute(replicationAgent, "QueueNumEntries") == 0) {
								log.info("Queue cleared successfully");
							} else {
								log.error("Could not clear queue successfully");
							}

						}
					}
				}
			}

		} catch (MalformedObjectNameException | InstanceNotFoundException | AttributeNotFoundException
				| ReflectionException | MBeanException e) {
			log.error(e.getMessage(), e);
		}
	}

	private void sendMessageToSlack() {

		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(
				"<your-slack-webhook-url>");

		try {
			StringEntity entity = new StringEntity("{\"channel\":\"#general\",\"text\":\"something from me!\"}");
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			client.execute(httpPost);
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
