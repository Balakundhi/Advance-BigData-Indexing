Rest API that can handle any structured data in Json
-->Specify URIs, status codes, headers, data model, version
Rest API with support for crd operations
-->Post, Get, Delete
Rest API with support for validation
-->◦Json Schema describing the data model for the use case
Controller validates incoming payloads against json schema
The semantics with ReST API operations such as update if not changed/read if changed
-->Update not required
-->Conditional read is required
Storage of data in key/value store
-->Must implement use case provided
