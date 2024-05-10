CREATE DATABASE `ramsey-dev` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

CREATE USER 'ramsey-user-dev'@'%' IDENTIFIED BY '<password>';
GRANT Alter ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Create ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Create view ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Delete ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Drop ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Index ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Insert ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT References ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Select ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Show view ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Trigger ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Update ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Alter routine ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Create routine ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Create temporary tables ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Execute ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';
GRANT Lock tables ON `ramsey-dev`.* TO 'ramsey-user-dev'@'%';

CREATE TABLE `ramsey-dev.client` (
                          `client_id` int NOT NULL AUTO_INCREMENT,
                          `subgraph_size` int DEFAULT NULL,
                          `vertex_count` int DEFAULT NULL,
                          `created_date` datetime(6) DEFAULT NULL,
                          `last_phone_home_date` datetime(6) DEFAULT NULL,
                          `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
                          `type` enum('CLIQUECHECKER','QUEUEMANAGER') DEFAULT NULL,
                          PRIMARY KEY (`client_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev.graph` (
                         `clique_count` int DEFAULT NULL,
                         `graph_id` int NOT NULL AUTO_INCREMENT,
                         `subgraph_size` int DEFAULT NULL,
                         `vertex_count` int DEFAULT NULL,
                         `identified_date` datetime(6) DEFAULT NULL,
                         `edge_data` text,
                         PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev.work_unit` (
                             `base_graph_id` int DEFAULT NULL,
                             `clique_count` int DEFAULT NULL,
                             `id` int NOT NULL AUTO_INCREMENT,
                             `subgraph_size` int DEFAULT NULL,
                             `vertex_count` int DEFAULT NULL,
                             `assigned_date` datetime(6) DEFAULT NULL,
                             `completed_date` datetime(6) DEFAULT NULL,
                             `created_date` datetime(6) DEFAULT NULL,
                             `processing_started_date` datetime(6) DEFAULT NULL,
                             `assigned_client` varchar(255) DEFAULT NULL,
                             `edges_to_flip` varchar(255) DEFAULT NULL,
                             `priority` enum('LOW','MEDIUM','HIGH') DEFAULT NULL,
                             `status` enum('NEW','ASSIGNED','COMPLETE','CANCELLED') DEFAULT NULL,
                             `work_unit_analysis_type` enum('COMPREHENSIVE','TARGETED','NAIVE') DEFAULT NULL,
                             PRIMARY KEY (`id`),
                             KEY `work_unit_subgraph_size_IDX` (`subgraph_size`,`vertex_count`,`status`,`assigned_client`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE DATABASE `ramsey-test` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

CREATE USER 'ramsey-user-test'@'%' IDENTIFIED BY '<password>';
GRANT Alter ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Create ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Create view ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Delete ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Drop ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Index ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Insert ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT References ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Select ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Show view ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Trigger ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Update ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Alter routine ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Create routine ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Create temporary tables ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Execute ON `ramsey-test`.* TO 'ramsey-user-test'@'%';
GRANT Lock tables ON `ramsey-test`.* TO 'ramsey-user-test'@'%';

CREATE TABLE `ramsey-test.client` (
                                     `client_id` int NOT NULL AUTO_INCREMENT,
                                     `subgraph_size` int DEFAULT NULL,
                                     `vertex_count` int DEFAULT NULL,
                                     `created_date` datetime(6) DEFAULT NULL,
                                     `last_phone_home_date` datetime(6) DEFAULT NULL,
                                     `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
                                     `type` enum('CLIQUECHECKER','QUEUEMANAGER') DEFAULT NULL,
                                     PRIMARY KEY (`client_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-test.graph` (
                                    `clique_count` int DEFAULT NULL,
                                    `graph_id` int NOT NULL AUTO_INCREMENT,
                                    `subgraph_size` int DEFAULT NULL,
                                    `vertex_count` int DEFAULT NULL,
                                    `identified_date` datetime(6) DEFAULT NULL,
                                    `edge_data` text,
                                    PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-test.work_unit` (
                                        `base_graph_id` int DEFAULT NULL,
                                        `clique_count` int DEFAULT NULL,
                                        `id` int NOT NULL AUTO_INCREMENT,
                                        `subgraph_size` int DEFAULT NULL,
                                        `vertex_count` int DEFAULT NULL,
                                        `assigned_date` datetime(6) DEFAULT NULL,
                                        `completed_date` datetime(6) DEFAULT NULL,
                                        `created_date` datetime(6) DEFAULT NULL,
                                        `processing_started_date` datetime(6) DEFAULT NULL,
                                        `assigned_client` varchar(255) DEFAULT NULL,
                                        `edges_to_flip` varchar(255) DEFAULT NULL,
                                        `priority` enum('LOW','MEDIUM','HIGH') DEFAULT NULL,
                                        `status` enum('NEW','ASSIGNED','COMPLETE','CANCELLED') DEFAULT NULL,
                                        `work_unit_analysis_type` enum('COMPREHENSIVE','TARGETED','NAIVE') DEFAULT NULL,
                                        PRIMARY KEY (`id`),
                                        KEY `work_unit_subgraph_size_IDX` (`subgraph_size`,`vertex_count`,`status`,`assigned_client`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE DATABASE `ramsey` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

CREATE USER 'ramsey-user'@'%' IDENTIFIED BY '<password>';
GRANT Alter ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Create ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Create view ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Delete ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Drop ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Index ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Insert ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT References ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Select ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Show view ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Trigger ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Update ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Alter routine ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Create routine ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Create temporary tables ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Execute ON `ramsey`.* TO 'ramsey-user'@'%';
GRANT Lock tables ON `ramsey`.* TO 'ramsey-user'@'%';

CREATE TABLE `ramsey.client` (
                                     `client_id` int NOT NULL AUTO_INCREMENT,
                                     `subgraph_size` int DEFAULT NULL,
                                     `vertex_count` int DEFAULT NULL,
                                     `created_date` datetime(6) DEFAULT NULL,
                                     `last_phone_home_date` datetime(6) DEFAULT NULL,
                                     `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
                                     `type` enum('CLIQUECHECKER','QUEUEMANAGER') DEFAULT NULL,
                                     PRIMARY KEY (`client_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey.graph` (
                                    `clique_count` int DEFAULT NULL,
                                    `graph_id` int NOT NULL AUTO_INCREMENT,
                                    `subgraph_size` int DEFAULT NULL,
                                    `vertex_count` int DEFAULT NULL,
                                    `identified_date` datetime(6) DEFAULT NULL,
                                    `edge_data` text,
                                    PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey.work_unit` (
                                        `base_graph_id` int DEFAULT NULL,
                                        `clique_count` int DEFAULT NULL,
                                        `id` int NOT NULL AUTO_INCREMENT,
                                        `subgraph_size` int DEFAULT NULL,
                                        `vertex_count` int DEFAULT NULL,
                                        `assigned_date` datetime(6) DEFAULT NULL,
                                        `completed_date` datetime(6) DEFAULT NULL,
                                        `created_date` datetime(6) DEFAULT NULL,
                                        `processing_started_date` datetime(6) DEFAULT NULL,
                                        `assigned_client` varchar(255) DEFAULT NULL,
                                        `edges_to_flip` varchar(255) DEFAULT NULL,
                                        `priority` enum('LOW','MEDIUM','HIGH') DEFAULT NULL,
                                        `status` enum('NEW','ASSIGNED','COMPLETE','CANCELLED') DEFAULT NULL,
                                        `work_unit_analysis_type` enum('COMPREHENSIVE','TARGETED','NAIVE') DEFAULT NULL,
                                        PRIMARY KEY (`id`),
                                        KEY `work_unit_subgraph_size_IDX` (`subgraph_size`,`vertex_count`,`status`,`assigned_client`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;