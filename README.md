# StelliMini

Project Overview:
The goal of this project was to create an application using the AWS Java SDK that provisions an 
AWS instance, sets up an apache server and hosts  a simple home page on that instance.  In addition the app
sets up automated monitoring using CloudWatch that sends alert emails whenever the instance
fails a status check.


Building Directions (using Maven):
First clone this git project locally with:
```
$git clone https://github.com/mikmike/StelliMini
```

Now change directories to the clone project.
```
$mvn package
```

The application builds into a self-contained, runnable jar file in the "target" folder of the project 
called stellimini-0.0.1-SNAPSHOT.jar

Usage:
Pre-requisites: This app requires AWS user credentials in order to operate.  The AWS user must have 
at least the following permissions: AmazonEC2FullAccess, AmazonSNSFullAccess


In order to run, first create a file anywhere on your file system called app.properties that 
contains the following properties:
```
accessKey=<your access key>
secretKey=<your secret key>
email=<your email adddress>
region=<target aws region; defaults to us-west-2>
```
Save this file in a convenient directory location.

To run the application:
```
$java -jar stellimini-0.0.1-SNAPSHOT.jar <path to app.properties>
```
For example if you saved your property file to /home/user/app.properties use:
```
$java -jar stellimini-0.0.1-SNAPSHOT.jar /home/user/app.properties
```

Notes: 
Each time the application runs it will create another separate t2.nano instance .   
Subsequent runs will see Warning messages as things like the security groups and key-pairs 
have already been created.


