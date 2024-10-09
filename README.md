# AWS Image Recognition Pipeline Project

Amazon Web Services is a powerful cloud platform that allows seamless management, monitoring, and access to resources over the web. This project leverages distributed computing through two distinct **EC2 instances** (Instance A and Instance B), which run in parallel and communicate via **AWS SQS** to process images stored in **AWS S3**. Each instance is responsible for a specific task—car detection and text recognition—working together within a distributed architecture. The project integrates AWS services for storage, message queuing, and machine learning to build an efficient, scalable solution.

![Project Overview](//AWSImageRecognition/aws/src/images/fig1.jpg)

### Key AWS Services Utilized
- **EC2**: Virtual machines for running the image recognition pipeline created from Amazon Linuz AMI.
- **S3**: Cloud storage that containing images for processing.
- **SQS**: Queue service for communication between the instances.
- **Rekognition**: AWS service used for image and text recognition.


<br>

# System Architecture


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

### S3 Storage

 - **Storing Images**:
   The images used for object and text recognition are stored in an S3 bucket. The bucket URL is: https://njit-cs-643.s3.us-east-1.amazonaws.com

In the following example, Instance A downloads an image (2.jpg) from the S3 bucket njit-cs-643 and saves it locally for Instance B. 

```java
// Initialize the S3 Client
S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();

// Define the S3 bucket and image key (file name)
String bucketName = "njit-cs-643";
String key = "2.jpg";  // Example image key

// Define where the downloaded file will be stored locally
Path destination = Paths.get("/home/ec2-user/images/2.jpg");

// Download the image from S3
s3.getObject(GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build(),
        destination);
```

### Simple Queue Service

 - **Instance A sends messages to SQS**
   - Instance A will process images from the S3 bucket and use **AWS Rekognition** to detect if there are cars. When a car is detected with at least a 90% confidence rate, Instance A sends the index of that image (e.g., `2.jpg`) to the SQS queue.
   - Instance A will also send a message (`-1`) to the queue when all images have been processed, which tells Instance B that there are no more images.

In the folowing code, **Instance A** sends messages to SQS.
   ```java
   // Import necessary AWS SDK packages
   import software.amazon.awssdk.services.sqs.SqsClient;
   import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

   // Initialize the SQS client
   SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();

   // Define the SQS queue URL
   String queueUrl = "https://sqs.us-east-1.amazonaws.com/your-account-id/your-queue-name";

   // Send image index to SQS
   String imageIndex = "2.jpg";
   sqs.sendMessage(SendMessageRequest.builder()
           .queueUrl(queueUrl)
           .messageBody(imageIndex)
           .build());

   // When done, send termination signal (-1)
   sqs.sendMessage(SendMessageRequest.builder()
           .queueUrl(queueUrl)
           .messageBody("-1")
           .build());
  ```

## AWS Rekognition

**AWS Rekognition** is used as the machine learning service to perform two key tasks:
1. **Object Detection**: Instance A uses Rekognition to detect cars in images.
2. **Text Recognition**: Instance B uses Rekognition to extract text from images that were flagged by Instance A as containing cars.


## Distributed Computing

### Parallel Execution

The two EC2 instances run in parallel, with **Instance A** detecting cars in images and **Instance B** performing text recognition based on the image indices it receives from the SQS queue. This creates a seamless and efficient pipeline for the instances to communicate.

### Communication Through SQS

To communicate between the two EC2 instances, **Amazon SQS** is used. **Instance A** sends the image indices to the SQS queue when it detects a car in an image. **Instance B** continuously polls the queue and processes the images as soon as a new message is received.

This decoupling allows the two instances to operate independently without waiting for each other, thus achieving distributed processing.

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

## Post-Launch Tasks
   - Verify SSH access to both instances:
     ```bash
     ssh -i /path/to/your-key.pem ec2-user@your-instance-public-ip
     ```
   - Set permissions for the `.pem` file:
     ```bash
     chmod 400 /path/to/your-key.pem
     ```

### 7. **AWS Credentials Setup**
   - Set up AWS credentials as environment variables:
     ```bash
     export AWS_ACCESS_KEY_ID=your-access-key
     export AWS_SECRET_ACCESS_KEY=your-secret-key
     export AWS_SESSION_TOKEN=your-session-token  
     ```

### 8. **Transfer the Project Files to Instance A**
   - Transfer project files to **Instance A** using `scp`:
     ```bash
     scp -i /path/to/your-key.pem -r /path/to/local-project-folder/ ec2-user@your-instance-public-ip:/home/ec2-user/
     ```

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
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.InstanceA
     ```

### 10. **Run the Project on Instance B**
   - Log into **Instance B** and repeat the process:
     ```bash
     mvn clean compile
     mvn clean package
     ```
   - Run the Java application for **Instance B**:
     ```bash
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.InstanceB
     ```

## Conclusion

This project successfully demonstrates how to build a distributed image recognition pipeline using AWS services. By utilizing two **EC2 instances** (Instance A and B), working in parallel, the pipeline efficiently handles image processing tasks such as car detection and text recognition. With **Amazon S3** providing scalable storage for images, **Amazon SQS** ensuring smooth communication between the instances, and **AWS Rekognition** offering powerful machine learning capabilities, the pipeline illustrates the effectiveness of cloud-based services for distributed computing.


The use of **AWS SDKs** in **Java** to integrate these services into a cohesive application demonstrates the ease with which developers can leverage cloud infrastructure to create scalable, reliable, and efficient systems. This project not only showcases distributed computing but also exemplifies the power and flexibility of AWS cloud services in real-world applications.
