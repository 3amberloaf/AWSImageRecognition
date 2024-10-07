package com.example.AWS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3ObjectSummary;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

public class AWS {

    public static void main(String[] args) throws Exception {
        // Set the AWS Region
        String clientRegion = "us-east-1";

        // Start InstanceA flow
        runInstanceA(clientRegion);

        // Start InstanceB flow (you can conditionally run this based on your requirement)
        runInstanceB(clientRegion);
    }

    // Common message listener class that works for both InstanceA and InstanceB
    static class MyListener implements MessageListener {

        private final RekognitionClient rekognitionClient;
        private final S3Client s3Client;
        private final String bucketName;

        public MyListener(String clientRegion, String bucketName) {
            this.rekognitionClient = RekognitionClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(clientRegion))
                    .build();
            this.s3Client = S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(clientRegion))
                    .build();
            this.bucketName = bucketName;
        }

        @Override
        public void onMessage(Message message) {
            try {
                ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(bucketName).build();
                ListObjectsV2Response result = s3Client.listObjectsV2(req);

                for (S3ObjectSummary objectSummary : result.contents()) {
                    String textMessage = ((TextMessage) message).getText();
                    if (objectSummary.key().contains(textMessage)) {
                        String photo = objectSummary.key();
                        // Text recognition from S3 image
                        DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                                .image(Image.builder()
                                        .s3Object(S3Object.builder()
                                                .bucket(bucketName)
                                                .name(photo)
                                                .build())
                                        .build())
                                .build();

                        try {
                            DetectTextResponse detectTextResult = rekognitionClient.detectText(detectTextRequest);
                            List<TextDetection> textDetections = detectTextResult.textDetections();
                            if (!textDetections.isEmpty()) {
                                System.out.print("Text Detected lines and words for:  " + photo + " ==> ");
                                for (TextDetection text : textDetections) {
                                    System.out.print("  Text Detected: " + text.detectedText() + " , Confidence: "
                                            + text.confidence());
                                    System.out.println();
                                }
                            }
                        } catch (RekognitionException e) {
                            System.out.print("Error during Rekognition operation");
                            e.printStackTrace();
                        }
                    }
                }
            } catch (JMSException e) {
                System.out.println("Please run the Instance-1 first...");
            }
        }
    }

    // InstanceA Flow
    public static void runInstanceA(String clientRegion) throws Exception {
        try {
            SqsClient sqsClient = SqsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(clientRegion))
                    .build();

            // Creating or waiting for the SQS Queue
            SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
                    AmazonSQSMessagingClientWrapper.wrapSqsClient(sqsClient));

            SQSConnection connection = connectionFactory.createConnection();
            AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

            if (!client.queueExists("MyQueue.fifo")) {
                Map<String, String> attributes = new HashMap<>();
                attributes.put("FifoQueue", "true");
                attributes.put("ContentBasedDeduplication", "true");
                client.createQueue(new CreateQueueRequest().withQueueName("MyQueue.fifo").withAttributes(attributes));
            }

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("MyQueue.fifo");
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(new MyListener(clientRegion, "njit-cs-643"));
            connection.start();

            Thread.sleep(10000); // Wait for incoming messages
        } catch (Exception e) {
            System.out.println("InstanceA: Please run the queue first.");
        }
    }

    // InstanceB Flow
    public static void runInstanceB(String clientRegion) throws Exception {
        try {
            SqsClient sqsClient = SqsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(clientRegion))
                    .build();

            // Similar logic for creating and listening to the SQS Queue
            SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(),
                    AmazonSQSMessagingClientWrapper.wrapSqsClient(sqsClient));

            SQSConnection connection = connectionFactory.createConnection();
            AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

            if (!client.queueExists("MyQueue.fifo")) {
                Map<String, String> attributes = new HashMap<>();
                attributes.put("FifoQueue", "true");
                attributes.put("ContentBasedDeduplication", "true");
                client.createQueue(new CreateQueueRequest().withQueueName("MyQueue.fifo").withAttributes(attributes));
            }

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("MyQueue.fifo");
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(new MyListener(clientRegion, "njit-cs-643"));
            connection.start();

            Thread.sleep(10000); // Wait for incoming messages
        } catch (Exception e) {
            System.out.println("InstanceB: Please run the queue first.");
        }
    }
}
