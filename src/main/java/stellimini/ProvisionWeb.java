package stellimini;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Properties;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.util.Base64;

public class ProvisionWeb {

	public static void main(String[] args) {
		
		//Load the application properties
		Properties props = new Properties();
		try{			
			props.load(new FileInputStream(new File(args[0])));
		}catch(IOException ioe){
			System.out.println("Error: Properties File Not Found");
			printUsage();
			System.exit(1);
		}
		
		String accessKey = props.getProperty("accessKey");
		String secretKey = props.getProperty("secretKey");
		String email	 = props.getProperty("email");
		String region 	 = props.getProperty("region", "us-west-2");		
		
		BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);		
		
		AmazonEC2Client ec2Client = new AmazonEC2Client(credentials);
		ec2Client.setEndpoint("ec2."+region+".amazonaws.com");

		//create a new security group with ports 80 and 22 open		
		CreateSecurityGroupRequest securityGroupRequest = new CreateSecurityGroupRequest();
		securityGroupRequest.withGroupName("sdk-security-group").withDescription("SDK security group");
		
		try{
			CreateSecurityGroupResult createSecurityGroupResult =
				ec2Client.createSecurityGroup(securityGroupRequest);
		}catch(Exception e){
			System.out.println("Warning: " + e);
		}
		
		IpPermission sshPermission = new IpPermission();
		sshPermission.withIpRanges("0.0.0.0/0")
		   .withIpProtocol("tcp")
		   .withFromPort(22)
		   .withToPort(22);
		
		IpPermission httpPermission = new IpPermission();
		httpPermission.withIpRanges("0.0.0.0/0")
		   .withIpProtocol("tcp")
		   .withFromPort(80)
		   .withToPort(80);
		
		ArrayList<IpPermission> ipPermissionList = new ArrayList<IpPermission>();
		ipPermissionList.add(sshPermission);
		ipPermissionList.add(httpPermission);
		
		
		AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
			    new AuthorizeSecurityGroupIngressRequest();

		authorizeSecurityGroupIngressRequest.withGroupName("sdk-security-group")
			                                    .withIpPermissions(ipPermissionList);
		
		try{
			ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
		}catch(Exception e){
			System.out.println("Warning: " + e);
		}
		
		// get a new set of keys for SSH access to new instance later on
		
		CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
		createKeyPairRequest.withKeyName("sdk-key-pair");
		
		try{
			CreateKeyPairResult createKeyPairResult =
				ec2Client.createKeyPair(createKeyPairRequest);
			KeyPair keyPair = new KeyPair();
			keyPair = createKeyPairResult.getKeyPair();
			String privateKey = keyPair.getKeyMaterial();
			System.out.println("New Private Key (Copy this to a .pem file for your SSH access):\n\n" + privateKey);		
		}catch(Exception e){
			System.out.println("Warning: " + e);
		}		

		//get host init settings from file init-commands.txt
		//this has the linux shell commands needed to install apache and create our home page
		
		String userData = "";
		try{
			
			BufferedReader br = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream("init-commands.txt")));
			String line = null;
			
			while((line = br.readLine()) != null){
				userData += line + "\n";
			}			
			br.close();
			userData = Base64.encodeAsString(userData.getBytes());			
			
		}catch(Exception e){
			System.out.println("Unable to load init settings: " + e);
		}
		
		//now, create the instance
		
		RunInstancesRequest runInstancesRequest =
			      new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-14ae4e74")
	      .withInstanceType("t2.nano")
	      .withUserData(userData)
	      .withMinCount(1)
	      .withMaxCount(1)
	      .withKeyName("sdk-key-pair")
	      .withSecurityGroups("sdk-security-group");
		
		
		RunInstancesResult runInstancesResult =
		      ec2Client.runInstances(runInstancesRequest); 
	  	
	  
		Instance newInstance = runInstancesResult.getReservation().getInstances().get(0);

		System.out.println("\n\nSuccess -- Your New Instance ID is:" + newInstance.getInstanceId());
		System.out.println("\nNote: it may take several minutes for the instance to fully initialize");
				
		
		//*EXTRA CREDIT* - This sets up an automated monitoring test for the new instance + sends alarms to the requested email		

		String TOPIC_NAME = "Instance_Health_Topic";

		AmazonSNSClient snsClient = new AmazonSNSClient(credentials);
		//note: make sure to set endpoints to same region as instance to monitor
		snsClient.setEndpoint("https://sns."+region+".amazonaws.com");

		
		//first we need to create a new SNS Topic and subscribe to it with our email address
		
		CreateTopicResult createTopicResult = snsClient.createTopic(TOPIC_NAME);
		SubscribeResult subscribeResult = snsClient.subscribe(createTopicResult.getTopicArn(), "email", email );		
		
		//now we create the monitoring metric via cloudwatch and publish alarms to our new topic
		
		AmazonCloudWatchClient cloudwatchClient = new AmazonCloudWatchClient(credentials);
		cloudwatchClient.setEndpoint("https://monitoring."+region+".amazonaws.com");
		
		PutMetricAlarmRequest ec2AlarmRequest = new PutMetricAlarmRequest();
		
		ArrayList<String> alarmActions = new ArrayList<String>();		
		alarmActions.add(createTopicResult.getTopicArn());	
		
		Dimension instanceDim = new Dimension();
		instanceDim.setName("InstanceId");		
		instanceDim.setValue(newInstance.getInstanceId());
		ArrayList<Dimension> dimensionList = new ArrayList<Dimension> ();
		dimensionList.add(instanceDim);
		 
		ec2AlarmRequest.withActionsEnabled(true)
					.withAlarmActions(alarmActions)
					.withNamespace("AWS/EC2")		
					.withAlarmName("Instance Status Check for " + newInstance.getInstanceId())
					.withAlarmDescription("Checks general status of the instance")
					.withStatistic(Statistic.Maximum)					
					.withMetricName("StatusCheckFailed")		
					.withThreshold(1d)
					.withComparisonOperator(ComparisonOperator.GreaterThanOrEqualToThreshold)
					.withPeriod(60)
					.withEvaluationPeriods(5)
					.withDimensions(dimensionList);
		cloudwatchClient.putMetricAlarm(ec2AlarmRequest);		
		
		System.out.println("\n\nMonitoring of this instance successfully enabled.  Alarms will be sent to " +email );		
		System.out.println("Done...");
	}

	public static void printUsage(){
		System.out.println("Usage: java -jar stellimini-0.0.1-SNAPSHOT.jar <properties-file>");
		System.out.println("Where <properties-file> defines the following properties (1 per line)");
		System.out.println("accessKey=<your aws access key>");
		System.out.println("secretKey=<your aws secret key>") ;
		System.out.println("email=<your email address>");
		System.out.println("region=<the aws region you want, defaults to us-west-2>");
		System.out.println("Exiting...");
	}
	
}
