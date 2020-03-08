# Icon Widget Analysis

The first part of DeepIntent is to extract <icon, texts, permission> triples. This phase mainly contains 2 steps.

1. Static Analysis

In static analysis step, we leverage several existing works (i.e., GATOR, IconIntent) to establish mappings between UI widgets and their corresponding handlers, and further mapping to sensitive APIs and permissions. The details and configurations could be see at [Static Analysis](Static_Analysis).

1. Contextual Text Extraction

In contextual text extraction, we use static analysis results to locate target icon and its corresponding layout, then extract embedded texts based on the icon and layout texts based on the layout file. The details and configurations could be see at [Contextual Text Extraction](ContextualTextExtraction).
