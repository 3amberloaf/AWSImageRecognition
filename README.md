# AWS EC2 Instance Setup for Image Recognition Project

## Project Overview
This project involves setting up two EC2 instances (Instance A and Instance B) that will work in parallel to perform image recognition tasks using AWS services such as EC2, S3, SQS, and Rekognition. This README outlines the steps taken to create the instances, configure them, transfer project files, set up AWS credentials, run the code on the instances, and handle image processing through AWS services.

## Steps to Create EC2 Instances

### 1. **Choose the AMI**
   - **Amazon Linux 2 AMI (HVM)** was selected to provide a stable and modern Linux environment with long-term support.
   - This AMI supports the latest versions of software packages and is optimized for AWS.

### 2. **Instance Type**
   - **Instance Type**: `t2.micro`
     - Chosen for its compatibility with the AWS Free Tier, which ensures no cost for running the instances (within the free tier usage limits).
     - Suitable for lightweight workloads.

### 3. **Key Pair Creation**
   - **Key Pair**: Created a new **RSA** key pair for secure SSH access to both instances.
     - The `.pem` file was downloaded and saved securely on the local machine.
     - The same key pair was used for both Instance A and Instance B to simplify management.

### 4. **Security Group Configuration**
   - A new **Security Group** was created with the following inbound rules:
     - **SSH (Port 22)**: Allowed only from the user's IP address for secure access to the instances.
     - **HTTP (Port 80)** and **HTTPS (Port 443)** were not enabled, as web traffic is not necessary for this project.
   - This ensures that the instances are only accessible via SSH from the specific IP address of the user, minimizing security risks.

### 5. **Launch Instances**
   - **Instance A**: Launched the first EC2 instance using Amazon Linux 2, t2.micro instance type, the previously created key pair, and the security group.
   - **Instance B**: Launched a second EC2 instance using the same configuration (Amazon Linux 2, t2.micro, same key pair, and same security group) to ensure consistency.

### 6. **Post-Launch Tasks**
   - After launching both instances, verified that SSH access works for both instances using the following command:
     ```bash
     ssh -i /path/to/your-key.pem ec2-user@your-instance-public-ip
     ```
   - Set the correct permissions for the `.pem` file:
     ```bash
     chmod 400 /path/to/your-key.pem
     ```

### 7. **AWS Credentials Setup**
   - Set up AWS credentials as environment variables on the local machine. These credentials are used by the Java AWS SDK to access AWS services (S3, SQS, and Rekognition).
   - Run the following commands in your terminal (substitute your actual credentials):
     ```bash
     export AWS_ACCESS_KEY_ID=your-access-key
     export AWS_SECRET_ACCESS_KEY=your-secret-key
     export AWS_SESSION_TOKEN=your-session-token  # If applicable
     ```
   - These credentials allow the program to interact with AWS services programmatically.

### 8. **Transfer the Project Files to Instance A**
   - To run the image recognition Java project on **Instance A**, the local project files were securely transferred to the instance using the `scp` command:
     ```bash
     scp -i /path/to/your-key.pem -r /path/to/local-project-folder/ ec2-user@your-instance-public-ip:/home/ec2-user/
     ```
   - Example:
     ```bash
     scp -i /Users/ambersautner/Desktop/Class/amber_sautner_aws_key.pem -r /Users/ambersautner/AWSImageRecognition/AWSProject/ ec2-user@ec2-18-206-176-12.compute-1.amazonaws.com:/home/ec2-user/
     ```
   - This command securely copies the entire project folder to the EC2 instance.

### 9. **Compile and Run the Project on Instance A**
   - After the project files were transferred to **Instance A**, logged into the EC2 instance using SSH and navigated to the project directory:
     ```bash
     ssh -i /path/to/your-key.pem ec2-user@your-instance-public-ip
     cd /home/ec2-user/AWSProject
     ```
   - **Compile the Java project**:
     ```bash
     mvn clean compile
     ```
   - **Package the Java project**:
     ```bash
     mvn clean package
     ```
   - **Run the Java application**:
     ```bash
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.InstanceA
     ```

### 10. **Run the Project on Instance B**
   - After completing setup for **Instance A**, moved to **Instance B** to process image data sent by Instance A.
   - Compiled and packaged the project similarly on **Instance B**:
     ```bash
     mvn clean compile
     mvn clean package
     ```
   - Then executed **Instance B**:
     ```bash
     java -cp target/AWSImageRecognition-1.0-SNAPSHOT.jar com.example.InstanceB
     ```

### 11. **Handling Errors and AWS Region Setup**
   - To resolve issues with ambiguous `Region` imports, ensured that the AWS `Region` class is fully qualified in the Java code:
     ```java
     software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.US_EAST_1;
     ```
   - Made sure the correct AWS region (`us-east-1`) is set for your services (S3, SQS, Rekognition).

### 12. **Processing Images**
   - Successfully executed **Instance B** to process images using AWS Rekognition and SQS.
   - Images were processed in real-time, and messages for the processed images were deleted from the SQS queue:
     ```bash
     mvn exec:java -Dexec.mainClass="com.example.InstanceB"
     ```

### 13. **Termination Reminder**
   - As the EC2 instances are running on the AWS Free Tier, it's important to **terminate** the instances once the project is completed to avoid incurring additional charges:
     - Go to **EC2 Dashboard** > **Instances**, select both instances, and click **Terminate**.

## Additional Notes
- Both instances were configured to communicate using AWS services such as S3, SQS, and Rekognition for the Image Recognition Pipeline project.
- It is crucial to monitor the usage and ensure the instances are terminated after completing the tasks to avoid unnecessary charges.
