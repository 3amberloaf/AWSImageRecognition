# Table of Contents

1. [Introduction](#aws-image-recognition-pipeline-project)
2. [System Architecture](#system-architecture)
   - [Key AWS Services Utilized](#key-aws-services-utilized)
   - [EC2 Instances](#ec2-instances)
     - [Instance A](#instance-a)
     - [Instance B](#instance-b)
   - [S3 Storage](#s3-storage)
   - [Simple Queue Service](#simple-queue-service)
3. [AWS Rekognition](#aws-rekognition)
4. [Cloud Environment Setup and Application Process](#cloud-environment-setup-and-application-process)
   - [EC2 Instances Creation](#ec2-instances-creation)
   - [Post-Launch Tasks](#post-launch-tasks)
   - [AWS Credentials Setup](#aws-credentials-setup)
   - [Transfer the Project Files to Instance A](#transfer-the-project-files-to-instance-a)
   - [Compile and Run the Project on Instance A](#compile-and-run-the-project-on-instance-a)
   - [Run the Project on Instance B](#run-the-project-on-instance-b)
5. [Conclusion](#conclusion)


# AWS Image Recognition Pipeline Project

Amazon Web Services is a powerful cloud platform that allows seamless management, monitoring, and access to resources over the web. This project leverages distributed computing through two distinct **EC2 instances** (Instance A and Instance B), which run in parallel and communicate via **AWS SQS** to process 10 images stored in **AWS S3**. The project integrates AWS services for storage, message queuing, and machine learning to build an efficient, scalable solution.

![Project Overview](//AWSImageRecognition/aws/src/images/fig1.jpg)




<br>

# System Architecture
### Key AWS Services Utilized
- **EC2**: Virtual machines for running the image recognition pipeline created from Amazon Linuz AMI.
- **S3**: Cloud storage that containing images for processing.
- **SQS**: Queue service for communication between the instances.
- **Rekognition**: AWS service used for image and text recognition.

## EC2 Instances
- **Instance A**: 
  - Reads images one by one from the S3 bucket.
  - Detects cars in images using AWS Rekognition.
  - Sends the index of images containing cars to an SQS queue.
  - Terminates by sending `-1` to the queue when there are no more images left.
  
- **Instance B**:
  - Reads image indices from SQS.
  - Downloads the corresponding images from S3.
  - Uses Rekognition to perform text recognition.
  - If both a car and text are found in an image, it writes the image’s index and the detected text to a file stored on Instance B's EBS (Elastic Block Store) volume.

## S3 Storage

 - **Storing Images**:
   The images used for object and text recognition are stored in an S3 bucket. The bucket URL is: https://njit-cs-643.s3.us-east-1.amazonaws.com

In the following example, Instance A downloads an image from S3 and saves it to a temporary path. 

```java

s3Client.getObject(GetObjectRequest.builder()
        .bucket(S3_BUCKET)
        .key(imageKey)
        .build(), ResponseTransformer.toFile(Paths.get(imagePath)));

```

## Simple Queue Service

 - **Instance A sends messages to SQS**
   - Instance A will process images from the S3 bucket and use **AWS Rekognition** to detect if there are cars. When a car is detected with at least a 90% confidence rate, Instance A sends the index of that image to the SQS queue.
   - Instance A will also send a message (`-1`) to the queue when all images have been processed, which tells Instance B that there are no more images for processing.

In the folowing code, **Instance A** sends messages to SQS when a car is detected.
   ```java

   SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
        .queueUrl(SQS_QUEUE_URL)
        .messageBody(messageBody)
        .build();

sqsClient.sendMessage(sendMsgRequest);

  ```

## AWS Rekognition

**AWS Rekognition** is used as the machine learning service to perform two key tasks:
1. **Object Detection**: Instance A uses Rekognition to detect cars in images.
2. **Text Recognition**: Instance B uses Rekognition to extract text from images that were flagged by Instance A as containing cars.

<br>

# Cloud Environment Setup and Application Process


## EC2 Instances Creation

Create two instances (Instance A and Instance B) with the following configuration:

- **Amazon Linux 2 AMI**: Provides a stable and modern Linux environment with long-term support.
- **Instance Type**: `t2.micro`
- **Key Pair**: Create an RSA key pair for secure SSH access and download the `.pem` file to your local machine.
- **Security Group**: Configure a security group with the following inbound rules:
  - SSH (Port 22): Set to MYIP
  - HTTP (Port 80): Set to MYIP
  - HTTPS (Port 443): Set to MYIP

## Instance Configuration Tasks
   - Verify SSH access to both instances:
     ```bash
     ssh -i /path/to/your-key.pem ec2-user@your-instance-public-ip
     ```
   - Set permissions for the `.pem` file:
     ```bash
     chmod 400 /path/to/your-key.pem
     ```

### 7. **AWS Credentials Setup**
   - Set up AWS credentials as environment variables in the instances:
     ```bash
     export AWS_ACCESS_KEY_ID=your-access-key
     export AWS_SECRET_ACCESS_KEY=your-secret-key
     export AWS_SESSION_TOKEN=your-session-token  
     ```

    * This can be accomplished through `nano ~/.aws/credentials`

### 8. **Transfer the Project Files to Instance A**
   - Transfer project files to **Instance A** using `scp`:
     ```bash
     scp -i /path/to/your-key.pem -r /path/to/local-project-folder/ ec2-user@your-instance-public-ip:/home/ec2-user/
     ```

     * Anytime the code is updated, the files will need to be transfered over again!

### 9. **Compile and Run the Project on Instance A**
   - Log into Instance A and navigate to the project directory:
     ```bash
     ssh -i /path/to/your-key.pem ec2-user@your-instance-public-ip
     cd /home/ec2-user/AWSProject
     ```
   - Compile and package the Java project:
     ```bash
     mvn clean compile
     mvn clean package
     ```
   - Run the Java application for **Instance A**:
     ```bash
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.Instances
     ```

### 10. Repeat Steps 8 and 9 for Instance B
   - Transfer the files through `scp`
   - Log into **Instance B** and repeat the process:
     ```bash
     mvn clean compile
     mvn clean package
     ```
   - Run the Java application for **Instance B**:
     ```bash
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.Instances
     ```

## Conclusion

This project successfully demonstrates how to build a distributed image recognition pipeline using AWS services. By utilizing two **EC2 instances** (Instance A and B), working in parallel, the pipeline is able to handle image processing tasks such as car detection and text recognition. With **Amazon S3** providing scalable storage for images, **Amazon SQS** ensuring smooth communication between the instances, and **AWS Rekognition** offering powerful machine learning capabilities, the pipeline illustrates the effectiveness of cloud-based services for distributed computing.

