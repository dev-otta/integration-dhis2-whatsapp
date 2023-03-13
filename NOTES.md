# Case 5 - WhatsApp-to-DHIS2 integration


- [Case 5 - WhatsApp-to-DHIS2 integration](#case-5---whatsapp-to-dhis2-integration)
  - [Overview](#overview)
    - [Objective](#objective)
    - [Scope](#scope)
    - [Long Term Goals](#long-term-goals)
  - [Structure](#structure)
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
  - [Troubleshooting / issues so far](#troubleshooting--issues-so-far)

// Todo:
  - Acknowledgdements
  - What do we expect to achieve?
  - Lessons learned
  - Design guide, blob, technical guide
## Overview
The project is a WhatsApp to DHIS2 integration for a neonatal hearing loss program at St. John's Medical College in Bengaluru, India. The system will help parents to easily answer questions regarding their infant’s progress in cases of possible hearing loss. The intended scope of the pilot project is 30-40 enrollments with the possibility of rapid scalability. This use case is a collaboration with HISP India, who has previously tried to integrate RapidPro with DHIS2.  
WhatsApp will connect to RapidPro using a Twilio channel, which again will integrate with DHIS2 using the existing RapidPro-to-DHIS2 connector. This integration will allow the neonatal hearing loss program to use WhatsApp as a channel to collect data through questionnaires and then seamlessly store and analyze the data in DHIS2. These questionnaires consist of simple yes/no questions for follow-up evaluation.  

### Objective
1.	An organization creates a program in DHIS2, which includes a series of program stages with questions with yes or no answers. The program stages relate to different time periods for follow-up, such as 3 months after enrollment. 
2.	The organization then creates a flow in RapidPro that connects to the different program stages in DHIS2 and allows individuals to respond to the questionnaire through WhatsApp.
3.	The RapidPro-to-DHIS2 connector synchronizes the program enrollments with RapidPro contacts, such that the RapidPro flows manage to send messages to the right phone numbers registered during the child’s enrollment.
4.	When an individual receives the questionnaire, they can reply with their answers through WhatsApp.
5.	The responses are then automatically sent to DHIS2 and stored in the appropriate fields.
6.	The organization can then use DHIS2 to analyze the data and generate reports.

### Scope
It is difficult at the current time of writing (26.01.2023) to give a precise scope estimate for the WhatsApp-to-DHIS2 integration. The number of users should be between 30-40 for the pilot project, so the system load requirements are quite limited during the first iteration. However, the project should be able to easily scale to support different use cases such as other event or aggregate data self-reporting.

The complexity of integration is somewhat limited because of the already existing DHIS2-RapidPro integration. The project therefore only needs to expand upon a previously implemented connector instead of developing something completely new. This reduces the scope of the planning and design phase but might lead to complications during implementation.   

The volume of the data that needs to be transferred between the systems is also quite limited. As of now, the system will only need to support simple yes or no answers passed as message flow results in RapidPro. The payload is therefore light. The number of messages is also limited during the pilot project given the limited number of participants. 

The project seems to converge to a point where user-controlled customization is required. Webhook calls and DHIS2 query parameters must be hardcoded in RapidPro to ensure correct data transfer. The integration therefore needs to be tailored to specific user needs, which in this case is questionnaire answers. 

Compared to the other DHIS2-RapidPro use cases, there is a greater security requirement in the WhatsApp-to-DHIS2 use case. This is particularly relevant during the contact synchronization, since the DHIS2 program enrollments contain sensitive information about the users such as full name, address, and phone number. It is therefore important that the data transfers are correctly encrypted and authorized. 

### Long Term Goals
The long term goals is to develop an extension of the already existing RapidPro-to-DHIS2 connector in a generalized way, such that the system can easily be configured for other use cases. This includes creating standalone Capture Routes in RapidPro-to-DHIS2 where event data transfer can be configured easily by using the application properties. i.e., setting “enable.event.transfer=true” should start the correct routes for polling RapidPro flows and passing it to events in DHIS2. 
A sub-goal might also be to simplify / create a detailed tutorial for hosting RapidPro servers, such that the full integration can be adopted by other organizations more easily. 

## Structure
The complete integration consists of multiple components that are connected through communication channels. As of now (26.01.2023) the different components are as follows, presented from endpoint to endpoint. The technical details of each component is presented in the following section.
* WhatsApp
* Twilio
* RapidPro
* DHIS2-RapidPro
* DHIS2 instance

## System Components
### WhatsApp
WhatsApp works as the user interface for the system. The app works by connecting to a user’s phone contact list, and uses phone numbers as the unique identifier for each user. The phone number currently used for the WhatsApp chatbot is provided by the twilio service. 

Connection to WhatsApp is established through the WhatsApp API. The WhatsApp Business API is a secure, scalable, and reliable way to connect to the WhatsApp Business app and send messages to users. To be able to use this service, the organization has to apply for a WhatsApp Business API account and be approved by WhatsApp. After approval, it is possible to use the API to send and receive messages, create customer profiles, manage customer sessions, and perform other tasks. In the case of this project, Twilio is used as a connector between the WhatsApp Business API and RapidPro. 

### Twilio
Twilio is a cloud communications platform that enables developers to add messaging, voice, and video capabilities to their applications. Key technical components of Twilio include APIs, a global network of telephony infrastructure, and a set of tools and services for managing communication channels and building messaging apps. In Twilio, it is easy to monitor and scale up the different messaging channels. The application also provides an easy way to aquire phone numbers that can be used as messaging channels for WhatsApp. In the case of this project, Twilio connects the RapidPro server with WhatsApp and provides the phone numbers used in WhatsApp as a connection endpoint for the users. 

### RapidPro
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

### RapidPro-to-DHIS2
RapidPro-to-DHIS2 is a connector developed by the integration team at DHIS2. The connector is a standalone Java-solution that integrates DHIS2 with RapidPro. DHIS2-to-RapidPro v2.0.0 provides the following:
* Routine synchronization of RapidPro contacts with DHIS2 users.
* Aggregate report transfer from RapidPro to DHIS2 via polling or webhook messaging.
* Automated reminders to RapidPro contacts when their aggregate reports are overdue. 

WhatsApp-to-DHIS2 will extend the RapidPro-to-DHIS2 connector by implementing the following functionality:
* Routine synchronization of RapidPro contacts with new program enrollments in DHIS2.
* Event data transfer from RapidPro to DHIS2 via polling or webhook messaging. 
* Automatic scheduling of flows after new enrollments in DHIS2. 

### DHIS2 Instance
Contains the program for the neonatal hearing loss project at st. Johns medical college in Bangaluru, India. There are in all four program stages that need to be connected with RapidPro flows. These program stages include follow-up questions regarding the state of the children enrolled in the program. The program stages are as follows:
* **Three months**
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

## Troubleshooting / issues so far
