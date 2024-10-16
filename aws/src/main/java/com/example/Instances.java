package com.example;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

// Main class 
public class Instances {

    // Static constants 
    private static final String S3_BUCKET = "njit-cs-643";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue";

    // Static variables for AWS services
    private static S3Client s3Client;
    private static RekognitionClient rekognitionClient;
    private static SqsClient sqsClient;

    // Main method to initialize AWS clients
    public static void main(String[] args) {
        initializeClients();

        // Create threads for Instance A and Instance B
        Thread instanceAThread = new Thread(new InstanceA());
        Thread instanceBThread = new Thread(new InstanceB());

        // Start both threads
        instanceAThread.start();
        instanceBThread.start();

        // Wait for both threads to finish
        try {
            instanceAThread.join();
            instanceBThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Initialize the AWS services 
    private static void initializeClients() {
        s3Client = S3Client.builder().build();
        rekognitionClient = RekognitionClient.builder().build();
        sqsClient = SqsClient.builder().build();
    }

    // Instance A for detecting cars
    static class InstanceA implements Runnable {
        // Override to define Instance logic to process images
        @Override
        public void run() {
            processImages();
        }

        // Loops through the 10 images and generated image key and temporary path
        private void processImages() {
            for (int i = 1; i <= 10; i++) {
                String imageKey = i + ".jpg";
                String imagePath = "/tmp/" + imageKey;  

                // Delete existing file if it exists
                try {
                    Files.deleteIfExists(Paths.get(imagePath));
                } catch (IOException e) {
                    System.err.println("Error deleting existing file: " + e.getMessage());
                }

                // Download the image from S3
                s3Client.getObject(GetObjectRequest.builder()
                        .bucket(S3_BUCKET)
                        .key(imageKey)
                        .build(), ResponseTransformer.toFile(Paths.get(imagePath)));

                // Perform object detection using Rekognition
                Image image = Image.builder()
                        .s3Object(S3Object.builder()
                                .bucket(S3_BUCKET)
                                .name(imageKey)
                                .build())
                        .build();

                // Creates request object for label detection with min of 90% confidence
                DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                        .image(image)
                        .minConfidence(90F)
                        .build();

                // Send request to AWS and return list of detected cars
                DetectLabelsResponse labelsResponse = rekognitionClient.detectLabels(detectLabelsRequest);
                List<Label> labels = labelsResponse.labels();

                // Check if any cars meet confidence requirement 
                boolean carDetected = labels.stream()
                        .anyMatch(label -> label.name().equalsIgnoreCase("Car") && label.confidence() > 90);

                // If car detected send to SQS and print message to console
                if (carDetected) {
                    String messageBody = i + "," + imageKey;
                    SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                            .queueUrl(SQS_QUEUE_URL)
                            .messageBody(messageBody)
                            .build();

                    sqsClient.sendMessage(sendMsgRequest);
                    System.out.println("Car detected in image: " + imageKey + ". Sent to SQS with index: " + i);
                } else {
                    System.out.println("No car detected in image: " + imageKey);
                }
            }

            // Output -1 when no more images are left
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .messageBody("-1")
                    .build());
        }
    }

    // Instance B for detecting text
    static class InstanceB implements Runnable {
        @Override
        public void run() {
            processTextDetection();
        }

        private void processTextDetection() {
            boolean processing = true;
            File outputFile = new File("/mnt/EBSB/output.txt");  // Writes to EBS on Instance B
            
            // Create or open the file in EBSB
            try {
                if (outputFile.createNewFile()) {
                    System.out.println("Output file created: " + outputFile.getAbsolutePath());
                } else {
                    System.out.println("Output file already exists: " + outputFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Error creating output file: " + e.getMessage());
            }

            // Processes images from SQS queue, waits 30 seconds if no messages
            while (processing) {
                ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                        .queueUrl(SQS_QUEUE_URL)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(30)
                        .build();

                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

                for (Message message : messages) {
                    String[] messageParts = message.body().split(",");
                    if (messageParts.length != 2) {
                        System.err.println("Invalid message format: " + message.body());
                        continue;
                    }

                    String index = messageParts[0].trim();
                    String imageKey = messageParts[1].trim();

                    // Stops processing images if recieved termination signal from Instance A
                    if (imageKey.equals("-1")) {
                        processing = false;
                        System.out.println("All images processed. Exiting.");
                        break;
                    }

                    // Download image from S3 to EBS
                    String localImagePath = "/mnt/EBSB/" + imageKey;
                    try {
                        Files.deleteIfExists(Paths.get(localImagePath));
                    } catch (IOException e) {
                        System.err.println("Error deleting existing file: " + e.getMessage());
                    }

                    s3Client.getObject(GetObjectRequest.builder()
                            .bucket(S3_BUCKET)
                            .key(imageKey)
                            .build(), Paths.get(localImagePath));

                    // Sends request to AWS for text recognition
                    DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                            .image(Image.builder()
                                    .s3Object(S3Object.builder()
                                            .bucket(S3_BUCKET)
                                            .name(imageKey)
                                            .build())
                                    .build())
                            .build();

                    DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);
                    List<TextDetection> textDetections = detectTextResponse.textDetections();

                    // Write the results to the output file in EBSB
                    if (!textDetections.isEmpty()) {
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true))) {
                            writer.write("=== Detected Text for Image Index: " + index + " ===\n");
                            writer.write("Image Key: " + imageKey + "\n");
                            writer.write("Detected text:\n");
                            for (TextDetection text : textDetections) {
                                writer.write("Detected: " + text.detectedText() + "\n");
                            }
                            writer.write("\n");
                        } catch (IOException e) {
                            System.err.println("Error writing to file: " + e.getMessage());
                        }
                    }

                    // Delete message from SQS
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(SQS_QUEUE_URL)
                            .receiptHandle(message.receiptHandle())
                            .build());

                    System.out.println("Processed and deleted message for image: " + imageKey);
                }
            }
        }
    }
}
