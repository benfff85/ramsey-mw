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

CREATE TABLE `ramsey-dev`.`graph` (
                         `clique_count` int DEFAULT NULL,
                         `graph_id` int NOT NULL AUTO_INCREMENT,
                         `subgraph_size` int DEFAULT NULL,
                         `vertex_count` int DEFAULT NULL,
                         `identified_date` datetime(6) DEFAULT NULL,
                         `edge_data` text,
                         PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev`.`stage` (
    `stage_id` int NOT NULL AUTO_INCREMENT,
    `base_graph_id` int DEFAULT NULL,
    `campaign_id` int DEFAULT NULL,
    `created_date` datetime(6) DEFAULT NULL,
    `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
    `updated_date` datetime(6) DEFAULT NULL,
    `work_enumeration_strategy` varchar(50) DEFAULT NULL,
    `details` text DEFAULT NULL,
    PRIMARY KEY (`stage_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev`.`campaign` (
    `campaign_id` int NOT NULL AUTO_INCREMENT,
    `created_date` datetime(6) DEFAULT NULL,
    `status` enum ('ACTIVE', 'INACTIVE') DEFAULT NULL,
    `strategy` enum ('COMPREHENSIVE_EDGE_PAIR_MUTATION') DEFAULT NULL,
    `subgraph_size` int DEFAULT NULL,
    `updated_date` datetime(6) DEFAULT NULL,
    `vertex_count` int DEFAULT NULL,
    `total_pairs` bigint DEFAULT NULL,
    PRIMARY KEY (`campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev`.`fleet` (
    `platform` varchar(32) NOT NULL,
    `campaign_id` int DEFAULT NULL,
    `status` enum ('RUNNING', 'PAUSED') NOT NULL DEFAULT 'RUNNING',
    `note` varchar(255) DEFAULT NULL,
    `updated_date` datetime(6) DEFAULT NULL,
    PRIMARY KEY (`platform`),
    CONSTRAINT `fk_fleet_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `ramsey-dev`.`campaign` (`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-dev`.work_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_graph_id INT,
    stage_id INT,
    edges_to_flip VARCHAR(255),
    clique_count INT,
    work_unit_analysis_type VARCHAR(50)
);



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

CREATE TABLE `ramsey-test`.`graph` (
                                    `clique_count` int DEFAULT NULL,
                                    `graph_id` int NOT NULL AUTO_INCREMENT,
                                    `subgraph_size` int DEFAULT NULL,
                                    `vertex_count` int DEFAULT NULL,
                                    `identified_date` datetime(6) DEFAULT NULL,
                                    `edge_data` text,
                                    PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-test`.`stage` (
                                      `stage_id` int NOT NULL AUTO_INCREMENT,
                                      `base_graph_id` int DEFAULT NULL,
                                      `campaign_id` int DEFAULT NULL,
                                      `created_date` datetime(6) DEFAULT NULL,
                                      `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
                                      `updated_date` datetime(6) DEFAULT NULL,
                                      `work_enumeration_strategy` varchar(50) DEFAULT NULL,
                                      `details` text DEFAULT NULL,
                                      PRIMARY KEY (`stage_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-test`.`campaign` (
                                         `campaign_id` int NOT NULL AUTO_INCREMENT,
                                         `created_date` datetime(6) DEFAULT NULL,
                                         `status` enum ('ACTIVE', 'INACTIVE') DEFAULT NULL,
                                         `strategy` enum ('COMPREHENSIVE_EDGE_PAIR_MUTATION') DEFAULT NULL,
                                         `subgraph_size` int DEFAULT NULL,
                                         `updated_date` datetime(6) DEFAULT NULL,
                                         `vertex_count` int DEFAULT NULL,
                                         `total_pairs` bigint DEFAULT NULL,
                                         PRIMARY KEY (`campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey-test`.`fleet` (
    `platform` varchar(32) NOT NULL,
    `campaign_id` int DEFAULT NULL,
    `status` enum ('RUNNING', 'PAUSED') NOT NULL DEFAULT 'RUNNING',
    `note` varchar(255) DEFAULT NULL,
    `updated_date` datetime(6) DEFAULT NULL,
    PRIMARY KEY (`platform`),
    CONSTRAINT `fk_fleet_campaign_test` FOREIGN KEY (`campaign_id`) REFERENCES `ramsey-test`.`campaign` (`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


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

CREATE TABLE `ramsey`.`graph` (
                                    `clique_count` int DEFAULT NULL,
                                    `graph_id` int NOT NULL AUTO_INCREMENT,
                                    `subgraph_size` int DEFAULT NULL,
                                    `vertex_count` int DEFAULT NULL,
                                    `identified_date` datetime(6) DEFAULT NULL,
                                    `edge_data` text,
                                    PRIMARY KEY (`graph_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey`.`stage` (
                                      `stage_id` int NOT NULL AUTO_INCREMENT,
                                      `base_graph_id` int DEFAULT NULL,
                                      `campaign_id` int DEFAULT NULL,
                                      `created_date` datetime(6) DEFAULT NULL,
                                      `status` enum('ACTIVE','INACTIVE') DEFAULT NULL,
                                      `updated_date` datetime(6) DEFAULT NULL,
                                      `work_enumeration_strategy` varchar(50) DEFAULT NULL,
                                      `details` text DEFAULT NULL,
                                      PRIMARY KEY (`stage_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ramsey`.`campaign` (
                                         `campaign_id` int NOT NULL AUTO_INCREMENT,
                                         `created_date` datetime(6) DEFAULT NULL,
                                         `status` enum ('ACTIVE', 'INACTIVE') DEFAULT NULL,
                                         `strategy` enum ('COMPREHENSIVE_EDGE_PAIR_MUTATION') DEFAULT NULL,
                                         `subgraph_size` int DEFAULT NULL,
                                         `updated_date` datetime(6) DEFAULT NULL,
                                         `vertex_count` int DEFAULT NULL,
                                         `total_pairs` bigint DEFAULT NULL,
                                         PRIMARY KEY (`campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;