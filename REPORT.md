# Case 5 - WhatsApp-to-DHIS2 integration


- [Case 5 - WhatsApp-to-DHIS2 integration](#case-5---whatsapp-to-dhis2-integration)
  - [Project Context](#project-context)
  - [Introduction to Case 5 - WhatsApp-to-DHIS2](#introduction-to-case-5---whatsapp-to-dhis2)
  - [Requirements](#requirements)
  - [Design](#design)
  - [Technical Description](#technical-description)
    - [Investigate best practices for middleware and integration architecture](#investigate-best-practices-for-middleware-and-integration-architecture)
    - [Investigate the existing RapidPro-DHIS2 connector](#investigate-the-existing-rapidpro-dhis2-connector)
    - [Identify how the existing RapidPro-DHIS2 connector must be expanded](#identify-how-the-existing-rapidpro-dhis2-connector-must-be-expanded)
    - [System Components](#system-components)
      - [WhatsApp](#whatsapp)
      - [Twilio](#twilio)
      - [RapidPro](#rapidpro)
      - [Early History](#early-history)
      - [From RapidSMS to RapidPro](#from-rapidsms-to-rapidpro)
      - [What is the difference between RapidPro and Textit?](#what-is-the-difference-between-rapidpro-and-textit)
      - [RapidPro-to-DHIS2](#rapidpro-to-dhis2)
      - [DHIS2 Instance](#dhis2-instance)
  - [Progress Updates](#progress-updates)
    - [Additions](#additions)
  - [Troubleshooting / issues so far](#troubleshooting--issues-so-far)


## Project Context
The WhatsApp-to-dhis2 use case is part of an ongoing project between HISP India and the audiology team at St. John’s College Hospital in Bangalore, India. At St. John’s, the audiology team has a neonatal hearing screening program with multiple follow-up stages. Until now there has only been manual data entry, and the parents must come into the hospital to attend the follow-up sessions. This has led to a high loss of follow-up, due to the parents not being able to come to the hospital to attend the follow-up sessions. 

In June 2022, HISP India was connected to St. John’s Hospital to aid in developing a mobile-based digital platform for minimum dataset capture to implement the neonatal hearing screening program. A tracker program was configured for the one-time registration of the neonates at the hospital after the birth. A lab technician will register the neonates after birth while the first hearing screening is conducted. A web interface and an Android application will be available to the lab technician for registration. The tracked entity attributed includes the neonates’ demographic details and hearing screening/audiometry results.

The neonatal hearing screening follow-up stages are represented as program stages in the tracker program. These are configured for regular follow-up of the neonates at specific intervals. This is done by scheduling each stage at an interval of 3 months, 6 months, 9 months, 1 year, 1.5 years and 2 years. The follow-up is done using the Android application. Once the lab technician completes registering the child in the web or Android application, a message will be sent to the parents’ contact number with the information to download and log in to the Android application. The lab technician will guide the parents to log in while they are at the facility. Once the login is completed, a device id of the parents’ phone will be generated and saved as a tracked entity attribute.

A manual consent form will be shared with the parents by the program team at St. John’s to confirm the agreement of the parents to participate in the study. (This will be added to the application in the second phase). An SMS will be sent to the parents as a reminder to log in to the Android application at each follow-up cycle. 
The team at St. John’s are still using manual registration but will begin to pilot the tracker program in late May. The soft deadline for this use case has therefore been set to **May 31st**.


## Introduction to Case 5 - WhatsApp-to-DHIS2
During development of the tracker program implementation, certain limitations with using the Android application to facilitate follow-up has been highlighted. While such an Android application have been successful in many ways, it also has certain limitations. One such limitation is requiring parents to download and use a separate application, which may not always be convenient or feasible and lead to loss of follow-up.    

To address these limitations, we propose a self-report approach to follow-up using WhatsApp, one of the most widely used messaging applications in the world. The approach builds on the existing system developed by HISP India and the team at St. John’s by allowing parents to self-report their child's hearing screening progress through WhatsApp rather than using a separate Android application. This approach offers several potential benefits.
* Increased convenience: parents can easily self-report their answers to the follow-up questionnaires through a platform they are already familiar with and use regularly. 
* Reduced loss to follow-up: Parents may be more likely to self-report their child’s hearing screening questionnaires via WhatsApp than attend in-person follow-up visits, reducing the risk of loss to follow-up and improving the accuracy of the data. 
*	Potential cost savings in terms of training and system maintenance. 

In this report, we describe case 5 of the ongoing project between Innovation Norway and DevOtta. A WhatsApp chatbot is developed that integrates with the already established DHIS2 instance and tracker program, allowing parents to self-report their neonates’ hearing screening progress through a familiar and accessible platform. We also present the results of our pilot study, which demonstrate the feasibility and potential impact of this approach. Overall, our use case represents a promising example of how chat-based platforms such as WhatsApp or Facebook Messenger can be used for self-reporting in DHIS2, leveraging a direct link between a user and the tracked entity they represent in DHIS2 without the need for a separate log-in page or application. Case 5 is an addition to an existing collaboration between HISP India and St. John’s Medical College Hospital and works as an alternative to the android application. 

## Requirements
* **WhatsApp-to-DHIS2 integration:** Develop an integration between the WhatsApp messaging platform and the DHIS2 instance, allowing for bi-directional communication between the two systems with support for:
  * Enrollment / tracked entity instance synchronization.
  * Tracker Event transfer. 
* The chatbot must support 30-40 new enrollments each day with the possibility of rapid scalability. 
* **Chatbot design and logic**:
  * As part of the integration, develop a chatbot for the questionnaires that includes the follow-up questions for 3 months, 6 months, 9 months, 1 year, 1.5 years and 2 years. The questionnaire should be designed with a logical flow and include validation rules to ensure that data is accurate and complete. If the user fails to answer the questionnaire, the chatbot logic should still save the questionnaire as complete, but with each question having the answer “no response from the mother”.
  * The chatbot must understand user inputs and provide appropriate responses, including automated messages and follow-up questions based on user responses.
  * The chatbot logic must be properly tested to ensure correct handling of edge cases (e.g., timeouts, unexpected answers from users, spam).
* **Data Transfer and Storage**: Develop or leverage existing integration frameworks to support data transfer from the chatbot to the DHIS2 instance and ensure that the data is stored in a secure and organized manner. 
* **Security and Privacy**
  * Ensure that the system is designed with strong security and privacy. The data shared between the two systems should be minimum, and all sensitive user information such as phone numbers and parent names must be handled correctly.
  * The system should be low-maintenance and, where it is feasible, use existing solutions. 


## Design
A tracker program has already been configured by the HISP India team. A lab technician will register the neonates after birth while the first hearing screening is conducted. During registration, a new tracked entity will be created and enrolled into the program. The tracked entity attributes must include the neonate mother’s contact details. This will be the main attribute for the enrolment / tracked entity instance synchronization. There must also be added attributes to uniquely identify the neonate on a technical level. Therefore, the enrollment id and tracked entity instance id should also be included as synchronized parameters. 

Once the neonate has been enrolled into the program, the WhatsApp chatbot will be used to facilitate communication between the DHIS2 instance and the mother. Specifically, the chatbot will send automated messages to the mother’s WhatsApp number, requesting answers to follow-up questionnaires at predefined time intervals. This will replace the need for the mothers to download the android application, since they will be contacted through a familiar messaging interface. 

To enable the chatbot to communicate with the DHIS2 Tracker program, an integration framework will be set up to listen for incoming messages from the chatbot. When a mother responds to a chatbot message, the integration layer will capture the response and update the corresponding program stage in the DHIS2 Tracker program. 

To ensure that the chatbot is user-friendly and accessible to mothers with varying levels of technical proficiency, the system will be designed to use simple and intuitive language, with clear instructions and prompts. E.g., only “Yes” or “No” answers should be allowed, and the chatbot should support timeouts and no response from the mother. The system will also be designed to handle errors and exceptions gracefully, providing helpful feedback and guidance to mothers who may be experiencing difficulties using the system.  

A consent form will be shared with the parents to confirm the agreement that their WhatsApp number will be shared with a WhatsApp chatbot. 

## Technical Description
The proposed system, as reflected in the design outline, consists of multiple components to achieve the WhatsApp-to-DHIS2 integration. Before starting any development work, one should therefore investigate similar solutions and identify integration- and chatbot frameworks to get an overview over what components are needed and how these components should be connected. 
### Investigate best practices for middleware and integration architecture
### Investigate the existing RapidPro-DHIS2 connector
### Identify how the existing RapidPro-DHIS2 connector must be expanded
### System Components
The complete integration consists of multiple components that are connected through communication channels. As of now (26.01.2023) the different components are as follows, presented from endpoint to endpoint. The technical details of each component is presented in the following section. Levarging existing 
* WhatsApp
* Twilio
* RapidPro
* DHIS2-RapidPro
* DHIS2 instance
#### WhatsApp
WhatsApp works as the user interface for the system. The app works by connecting to a user’s phone contact list, and uses phone numbers as the unique identifier for each user. The phone number currently used for the WhatsApp chatbot is provided by the twilio service. 

Connection to WhatsApp is established through the WhatsApp API. The WhatsApp Business API is a secure, scalable, and reliable way to connect to the WhatsApp Business app and send messages to users. To be able to use this service, the organization has to apply for a WhatsApp Business API account and be approved by WhatsApp. After approval, it is possible to use the API to send and receive messages, create customer profiles, manage customer sessions, and perform other tasks. In the case of this project, Twilio is used as a connector between the WhatsApp Business API and RapidPro. 

#### Twilio
Twilio is a cloud communications platform that enables developers to add messaging, voice, and video capabilities to their applications. Key technical components of Twilio include APIs, a global network of telephony infrastructure, and a set of tools and services for managing communication channels and building messaging apps. In Twilio, it is easy to monitor and scale up the different messaging channels. The application also provides an easy way to aquire phone numbers that can be used as messaging channels for WhatsApp. In the case of this project, Twilio connects the RapidPro server with WhatsApp and provides the phone numbers used in WhatsApp as a connection endpoint for the users. 

#### RapidPro
RapidPro is an open-source platform for building and deploying messaging and voice applications. Key technical components of RapidPro include a visual interface for building workflows, APIs for integrating with other systems, and a cloud-based infrastructure for managing and running applications. RapidPro is therefore used to create the message flows needed to answer the questionnares used in the neonatal hearing loss program follow-up. As of 30.01.2023 we have managed to run a local development instance of RapidPro for a Linux system. We followed this tutorial: [RapidPro installation guide](https://riseuplabs.com/rapidpro-installation-guide/). It is, however, difficult to host such an instance to the cloud. We are therefore using RapidPro's freely hosted server called Textit during development. 

#### Early History 
RapidPro is a UNICEF project started as early as 2007 in the form of RapidSMS. RapidSMS was created to support ongoing data collection efforts and youth engagement activities. RapidSMS was a free and open source framework designed to send and receive data using basic mobile phones, manage complex workflows, automate analysis and present data in real-time. Zambia, for example, used RapidSMS to facilitate communication between clinics and community health workers to reduce the amount of time between collecting blood samples for early infant diagnosis for HIV and the return of test results to the originating health facility. RapidSMS gained traction in international development, and many organizations contributed to the codebase.  

(Source: https://rapidpro.github.io/rapidpro/)  

RapidPro was originally designed to be a tool for sending and receiving SMS messages in low-resource settings. The platform was built using the Python programming language and the Django web framework. It was specifically designed to be easily customizable and adaptable to different use cases, something that is still an important criteria of RapidPro today.  

Inspired by rapidSMS’s capabilities and informed by their experience using RapidSMS, Rwandan software engineering firm Nyaruka (now called Textit) built their own SMS service called Textit – a commercial hosted service that runs on RapidPro. Textit was built from the experience that Nyaruka gained from building many RapidSMS systems for various clients. The experience informed their decision to build a system that was quick to deploy and which allowed for rapid experimentations of various interventions. (Source https://rapidpro.github.io/rapidpro/about/)  

#### From RapidSMS to RapidPro
The early history of RapidPro can be traced back to 2012, when Nyaruka was commissioned by UNICEF to develop an interactive voice response (IVR) system to improve maternal and child health outcomes in Rwanda. The system, called the RapidSMS Health Module, was built on top of the RapidSMS platform, which was also developed by Nyaruka. 

Building on the success of the RapidSMS Health Module, Nyaruka continued to develop and refine the platform, eventually creating a new version of the software that was specifically designed for creating interactive messaging systems. While not directly based on RapidSMS, Textit was informed and in some ways inspired by Nyaruka’s experience using it.  This new platform, which was later named RapidPro, was first released in 2014. This platform was a partnership between UNICEF and Textit.  

The initial version of RapidPro was relatively simple, but it provided a flexible and powerful tool for creating a wide range of messaging systems. The platform included a visual flow editor that allowed users to create complex conversation trees, as well as a powerful API that enabled integration with other software and services. 

Over the years, RapidPro has continued to evolve and improve. In 2015, Nyaruka received a grant from the Bill and Melinda Gates Foundation to build a new feature that would allow users to create chatbots on the platform. This led to the development of the Flow XO integration, which allows users to create chatbots that can interact with users in a natural and intuitive way. 

#### What is the difference between RapidPro and Textit?
Textit is a hosted RapidPro Provider. Textit was developed by the same company that created RapidPro, Nyaruka, and it is designed to be easy to use and highly customizable. Changes to Textit are rolled into RapidPro, and changes to RapidPro are likewise rolled into Textit. The difference between RapidPro and Textit is that Textit is hosted for you and you pay for that hosting, whereas RapidPro is the open-source version that you need to host yourself.  

“if you think TextIt is too expensive, then you definitely cannot afford to host RapidPro in a reliable manner yourself.” - https://blog.textit.in/textit-open-sources-technology-platform-as-rapidpro 

#### RapidPro-to-DHIS2
RapidPro-to-DHIS2 is a connector developed by the integration team at DHIS2. The connector is a standalone Java-solution that integrates DHIS2 with RapidPro. DHIS2-to-RapidPro v2.0.0 provides the following:
* Routine synchronization of RapidPro contacts with DHIS2 users.
* Aggregate report transfer from RapidPro to DHIS2 via polling or webhook messaging.
* Automated reminders to RapidPro contacts when their aggregate reports are overdue. 

WhatsApp-to-DHIS2 will extend the RapidPro-to-DHIS2 connector by implementing the following functionality:
* Routine synchronization of RapidPro contacts with new program enrollments in DHIS2.
* Event data transfer from RapidPro to DHIS2 via polling or webhook messaging. 
* Automatic scheduling of flows after new enrollments in DHIS2. 

#### DHIS2 Instance
Contains the program for the neonatal hearing loss project at st. Johns medical college in Bangaluru, India. There are in all four program stages that need to be connected with RapidPro flows. These program stages include follow-up questions regarding the state of the children enrolled in the program. The program stages are as follows:
* **3 months**
  * Does the child turn its head to a new sound?
  * As your child is falling aslepp, does a "sssh" sound wake up the child?
  * Does your child utter a different sound for hunger & happiness?
  * Does your child do "cooing" or "gooing"?
* **9 months**
  * Does your child turn head and look when called by name?
  * Does your child play sign games like "koo-kee"?
  * Does your child produce sounds such as "P" / "B" / "M"?
* **1 year**
  * Does your child understand simple commands?
  * Does your child say one meaningful word like "Appa" or "Amma"?
* **1.5 years**
  * Does your child point to body parts such as nose, mouth?
  * Does your child respond to "Bye-Bye"?
  * Does your child say at least 5 words you can understand?

## Progress Updates
### Additions
  * The choice of components must reflect what is stated in the requirements and design. 
  * Add pictures of the program stages and rapidPro flows. Show how they are conntected and how you can use this approach for other Use Cases. 
  * Add "stakeholders" (entities that care of the project in some way). DevOtta, UNICEF, Uio, HISP India, St. Johns Medical College Hospital, and to some extent, audiology teams in neighboring hospitals.   
  * Actors: Lab Technicians and doctors at st. John Hospital. Parents of children enrolled into the neonatal hearing loss program. HISP India
  * Country team participants. DevOtta in collaboration with HISP India and UiO. 
  * For each subsection in the technical description, try to answer "what" "why" and "how"?
  * Docs: connecting whatsapp to RapidPro. Connecting RapidPro to DHIS2. 
## Troubleshooting / issues so far
