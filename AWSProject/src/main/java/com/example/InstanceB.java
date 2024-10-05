package com.example;


import java.util.List;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public class InstanceB {
    

    public static void main(String[] args) {
        // Initialize SQS client
        SqsClient sqsClient = SqsClient.builder().build();
        
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue"; 

        boolean processing = true;
        while (processing) {
            // Receive messages from SQS queue
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(20)  // Long polling to avoid constant polling
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            for (Message message : messages) {
                String imageKey = message.body();

                if (imageKey.equals("-1")) {
                    // -1 is the signal from Instance A that all images are processed
                    processing = false;
                    System.out.println("All images processed. Exiting.");
                    break;
                }

                // Process the image (e.g., further analyze the image, log, etc.)
                System.out.println("Processing image: " + imageKey);

                // After processing, delete the message from the queue
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(sqsQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
                System.out.println("Deleted message for image: " + imageKey);
            }
        }
    }
}
