**SHOPMIND DECISION LOG**

This document captures key product, design, and technical decisions made during the development of ShopMind, including the rationale and tradeoffs behind each decision.

| ID | Category | Decision | Why we Chose it | Tradeoff |
| :---- | :---- | :---- | :---- | :---- |
| D001 | Product | Pivoted from website to app-based experience  | Conversational shopping aligned better with AI-guided discovery  | More UI complexity  |
| D002 | Scope | Adopted shoes-only MVP  | Faster iteration and stronger recommendation quality  | Limited product scope  |
| D003 | Technical | Used CSV product ingestion instead of live Shopify integration  | Shopify paid APIs limited real-time integration; CSV follows Shopify import schema  | No live inventory |
| D004 | Technical | Migrated backend from Express.js to Spring Boot Framework | Better scalability, security, and multi-user handling  | Higher setup complexity |
| D005 | Design  | Changed UI from red/black to white/black  | More premium and trustworthy experience  | Redesign effort |
| D006 | Product | Used conversational discovery instead of filters  | Reduced decision fatigue  | More backend logic |
| D007 | AI/Product | Replaced confidence percentage with Fit Score | More explainable recommendations  | Requires more user context |
| D008 | Product | Added explainable recommendations  | Builds trust in recommendations  | More recommendation logic |
| D009 | Architecture | Session-based recommendation flow  | Supports multiple users and memory  | More backend complexity |
| D010 | Strategy | Focused on honest MVP  | Better execution over fake AI claims  | Smaller feature scope |
| D011 | Product Roadmap  | Planned expansion from shoes → footwear → multi-category commerce  | Controlled scaling while maintaining recommendation quality  | Slower expansion  |

