# AWS EC2 Instance Setup for Image Recognition Project

## Project Overview
This project involves setting up two EC2 instances (Instance A and Instance B) that will work in parallel to perform image recognition tasks using AWS services such as EC2, S3, SQS, and Rekognition. This README outlines the steps taken to create the instances and configure them for the project.

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

### 7. **Termination Reminder**
   - As the EC2 instances are running on the AWS Free Tier, it's important to **terminate** the instances once the project is completed to avoid incurring additional charges:
     - Go to **EC2 Dashboard** > **Instances**, select both instances, and click **Terminate**.

## Additional Notes
- Both instances were configured to communicate using AWS services such as S3, SQS, and Rekognition for the Image Recognition Pipeline project.
- It is crucial to monitor the usage and ensure the instances are terminated after completing the tasks to avoid unnecessary charges.
