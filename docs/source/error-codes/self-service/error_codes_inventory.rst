Error group: KVErrors / Internal
---------------------------------------

Errors that arise from an internal system misbehavior.



Error code: INTERNALLY_DUPLICATE_KEYS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The participant didn't detect an attempt by the transaction submission to use the same key for two active contracts.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: INTERNALLY_INCONSISTENT_KEYS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The participant didn't detect an attempt by the transaction submission to use a stale contract key.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: INVALID_PARTICIPANT_STATE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: An invalid participant state has been detected.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: MISSING_INPUT_STATE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The participant didn't provide a necessary transaction submission input.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: REJECTION_REASON_NOT_SET
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: A rejection reason has not been set.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: VALIDATION_FAILURE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: An invalid transaction submission was not detected by the participant.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.




Error group: KVErrors / Resources
---------------------------------------

Errors that relate to system resources.



Error code: RESOURCE_EXHAUSTED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: A system resource has been exhausted.

    **Category**: ContentionOnSharedResources

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status ABORTED including a detailed error message

    **Resolution**: Retry the transaction submission or provide the details to the participant operator.




Error group: KVErrors / Time
---------------------------------------

Errors that relate to the Daml concepts of time.



Error code: CAUSAL_MONOTONICITY_VIOLATED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: At least one input contract's ledger time is later than that of the submitted transaction.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Retry the transaction submission.



Error code: INVALID_RECORD_TIME
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The record time is not within bounds for reasons other than deduplication, such as excessive latency. Excessive clock skew between the participant and the committer or a time model that is too restrictive may also produce this rejection.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Retry the submission or contact the participant operator.



Error code: RECORD_TIME_OUT_OF_RANGE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The record time is not within bounds for reasons other than deduplication, such as excessive latency. Excessive clock skew between the participant and the committer or a time model that is too restrictive may also produce this rejection.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Retry the transaction submission or contact the participant operator.




Error group: ParticipantErrorGroup / IndexErrors / DatabaseErrors
---------------------------------------





Error code: INDEX_DB_INVALID_RESULT_SET
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if the result set returned by a query against the Index database is invalid.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: INDEX_DB_SQL_NON_TRANSIENT_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a non-transient error arises when executing a query against the index database.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact the participant operator.



Error code: INDEX_DB_SQL_TRANSIENT_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a transient error arises when executing a query against the index database.

    **Category**: TransientServerFailure

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status UNAVAILABLE including a detailed error message

    **Resolution**: Re-submit the request.




Error group: ParticipantErrorGroup / LedgerApiErrors
---------------------------------------





Error code: CONFIGURATION_ENTRY_REJECTED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a new configuration is rejected.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Fetch newest configuration and/or retry.



Error code: LEDGER_API_INTERNAL_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if there was an unexpected error in the Ledger API.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: PACKAGE_UPLOAD_REJECTED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a package upload is rejected.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Refer to the detailed message of the received error.



Error code: PARTICIPANT_BACKPRESSURE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs when a participant rejects a command due to excessive load. Load can be caused by the following factors: 1. when commands are submitted to the participant through its Ledger API, 2. when the participant receives requests from other participants through a connected domain.

    **Category**: ContentionOnSharedResources

    **Conveyance**: This error is logged with log-level WARN on the server side. This error is exposed on the API with grpc-status ABORTED including a detailed error message

    **Resolution**: Wait a bit and retry, preferably with some backoff factor. If possible, ask other participants to send fewer requests; the domain operator can enforce this by imposing a rate limit.



Error code: REQUEST_TIME_OUT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a request processing status is not known and a time-out is reached.

    **Category**: DeadlineExceededRequestStateUnknown

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status DEADLINE_EXCEEDED including a detailed error message

    **Resolution**: Retry for transient problems. If non-transient contact the operator as the time-out limit might be too short.



Error code: SERVICE_NOT_RUNNING
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when the requested service has already been closed.

    **Category**: TransientServerFailure

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status UNAVAILABLE including a detailed error message

    **Resolution**: Retry re-submitting the request. If the error persists, contact the participant operator.




Error group: ParticipantErrorGroup / LedgerApiErrors / AuthorizationChecks
---------------------------------------





Error code: INTERNAL_AUTHORIZATION_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: An internal system authorization error occurred.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact the participant operator.



Error code: PERMISSION_DENIED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given if the supplied authorization token is not sufficient for the intended command. The exact reason is logged on the participant, but not given to the user for security reasons.

    **Category**: InsufficientPermission

    **Conveyance**: This error is logged with log-level WARN on the server side. This error is exposed on the API with grpc-status PERMISSION_DENIED without any details due to security reasons

    **Resolution**: Inspect your command and your token or ask your participant operator for an explanation why this command failed.



Error code: UNAUTHENTICATED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given if the submitted command does not contain a JWT token on a participant enforcing JWT authentication.

    **Category**: AuthInterceptorInvalidAuthenticationCredentials

    **Conveyance**: This error is logged with log-level WARN on the server side. This error is exposed on the API with grpc-status UNAUTHENTICATED without any details due to security reasons

    **Resolution**: Ask your participant operator to provide you with an appropriate JWT token.




Error group: ParticipantErrorGroup / LedgerApiErrors / CommandExecution
---------------------------------------





Error code: FAILED_TO_DETERMINE_LEDGER_TIME
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if the participant fails to determine the max ledger time of the used contracts. Most likely, this means that one of the contracts is not active anymore which can happen under contention. It can also happen with contract keys. 

    **Category**: ContentionOnSharedResources

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status ABORTED including a detailed error message

    **Resolution**: Retry the transaction submission.




Error group: ParticipantErrorGroup / LedgerApiErrors / CommandExecution / Interpreter
---------------------------------------





Error code: CONTRACT_NOT_ACTIVE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if an exercise or fetch happens on a transaction-locally consumed contract.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: This error indicates an application error.



Error code: DAML_AUTHORIZATION_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a Daml transaction fails due to an authorization error. An authorization means that the Daml transaction computed a different set of required submitters than you have provided during the submission as `actAs` parties.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: This error type occurs if there is an application error.



Error code: DAML_INTERPRETATION_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a Daml transaction fails during interpretation.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: This error type occurs if there is an application error.



Error code: DAML_INTERPRETER_INVALID_ARGUMENT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a Daml transaction fails during interpretation due to an invalid argument.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: This error type occurs if there is an application error.




Error group: ParticipantErrorGroup / LedgerApiErrors / CommandExecution / Interpreter / LookupErrors
---------------------------------------





Error code: CONTRACT_KEY_NOT_FOUND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if the Daml engine interpreter cannot resolve a contract key to an active contract. This can be caused by either the contract key not being known to the participant, or not being known to the submitting parties or the contract representing an already archived key.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: This error type occurs if there is contention on a contract.




Error group: ParticipantErrorGroup / LedgerApiErrors / CommandExecution / Package
---------------------------------------





Error code: ALLOWED_LANGUAGE_VERSIONS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the uploaded DAR is based on an unsupported language version.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Use a DAR compiled with a language version that this participant supports.



Error code: PACKAGE_VALIDATION_FAILED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a package referred to by a command fails validation. This should not happen as packages are validated when being uploaded.

    **Category**: MaliciousOrFaultyBehaviour

    **Conveyance**: This error is logged with log-level WARN on the server side. This error is exposed on the API with grpc-status UNKNOWN without any details due to security reasons

    **Resolution**: Contact support.




Error group: ParticipantErrorGroup / LedgerApiErrors / CommandExecution / Preprocessing
---------------------------------------





Error code: COMMAND_PREPROCESSING_FAILED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if a command fails during interpreter pre-processing.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect error details and correct your application.




Error group: ParticipantErrorGroup / LedgerApiErrors / ConsistencyErrors
---------------------------------------





Error code: CONTRACT_NOT_FOUND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error occurs if the Daml engine can not find a referenced contract. This can be caused by either the contract not being known to the participant, or not being known to the submitting parties or already being archived.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: This error type occurs if there is contention on a contract.



Error code: DUPLICATE_COMMAND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: A command with the given command id has already been successfully processed.

    **Category**: InvalidGivenCurrentSystemStateResourceExists

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status ALREADY_EXISTS including a detailed error message

    **Resolution**: The correct resolution depends on the use case. If the error received pertains to a submission retried due to a timeout, do nothing, as the previous command has already been accepted. If the intent is to submit a new command, re-submit using a distinct command id.  



Error code: DUPLICATE_CONTRACT_KEY
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error signals that within the transaction we got to a point where two contracts with the same key were active.

    **Category**: InvalidGivenCurrentSystemStateResourceExists

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status ALREADY_EXISTS including a detailed error message

    **Resolution**: This error indicates an application error.



Error code: INCONSISTENT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: At least one input has been altered by a concurrent transaction submission.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: The correct resolution depends on the business flow, for example it may be possible to proceed without an archived contract as an input, or the transaction submission may be retried to load the up-to-date value of a contract key.



Error code: INCONSISTENT_CONTRACTS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: An input contract has been archived by a concurrent transaction submission.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: The correct resolution depends on the business flow, for example it may be possible to proceed without the archived contract as an input, or a different contract could be used.



Error code: INCONSISTENT_CONTRACT_KEY
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: An input contract key was re-assigned to a different contract by a concurrent transaction submission.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Retry the transaction submission.



Error code: INVALID_LEDGER_TIME
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The ledger time of the submission violated some constraint on the ledger time.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Retry the transaction submission.




Error group: ParticipantErrorGroup / LedgerApiErrors / PackageServiceError
---------------------------------------





Error code: DAR_NOT_SELF_CONSISTENT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the uploaded Dar is broken because it is missing internal dependencies.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Contact the supplier of the Dar.



Error code: DAR_VALIDATION_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the validation of the uploaded dar failed.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the error message and contact support.



Error code: PACKAGE_SERVICE_INTERNAL_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates an internal issue within the package service.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Inspect the error message and contact support.




Error group: ParticipantErrorGroup / LedgerApiErrors / PackageServiceError / Reading
---------------------------------------





Error code: DAR_PARSE_ERROR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the content of the Dar file could not be parsed successfully.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the error message and contact support.



Error code: INVALID_DAR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the supplied dar file was invalid.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the error message for details and contact support.



Error code: INVALID_DAR_FILE_NAME
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the supplied dar file name did not meet the requirements to be stored in the persistence store.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect error message for details and change the file name accordingly



Error code: INVALID_LEGACY_DAR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the supplied zipped dar is an unsupported legacy Dar.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Please use a more recent dar version.



Error code: INVALID_ZIP_ENTRY
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the supplied zipped dar file was invalid.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the error message for details and contact support.



Error code: ZIP_BOMB
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error indicates that the supplied zipped dar is regarded as zip-bomb.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the dar and contact support.




Error group: ParticipantErrorGroup / LedgerApiErrors / RequestValidation
---------------------------------------





Error code: INVALID_ARGUMENT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error is emitted when a submitted ledger API command contains an invalid argument.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the reason given and correct your application.



Error code: INVALID_DEDUPLICATION_PERIOD
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error is emitted when a submitted ledger API command specifies an invalid deduplication period.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Inspect the error message, adjust the value of the deduplication period or ask the participant operator to increase the maximum deduplication period.



Error code: INVALID_FIELD
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error is emitted when a submitted ledger API command contains a field value that cannot be understood.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the reason given and correct your application.



Error code: LEDGER_ID_MISMATCH
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: Every ledger API command contains a ledger-id which is verified against the running ledger.           This error indicates that the provided ledger-id does not match the expected one.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Ensure that your application is correctly configured to use the correct ledger.



Error code: MISSING_FIELD
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This error is emitted when a mandatory field is not set in a submitted ledger API command.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Inspect the reason given and correct your application.



Error code: NON_HEXADECIMAL_OFFSET
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The supplied offset could not be converted to a binary offset.

    **Category**: InvalidIndependentOfSystemState

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status INVALID_ARGUMENT including a detailed error message

    **Resolution**: Ensure the offset is specified as a hexadecimal string.



Error code: OFFSET_AFTER_LEDGER_END
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a read request uses an offset beyond the current ledger end.

    **Category**: InvalidGivenCurrentSystemStateSeekAfterEnd

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status OUT_OF_RANGE including a detailed error message

    **Resolution**: Use an offset that is before the ledger end.



Error code: OFFSET_OUT_OF_RANGE
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a read request uses an offset invalid in the requests' context.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Inspect the error message and use a valid offset.



Error code: PARTICIPANT_PRUNED_DATA_ACCESSED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a read request tries to access pruned data.

    **Category**: InvalidGivenCurrentSystemStateOther

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status FAILED_PRECONDITION including a detailed error message

    **Resolution**: Use an offset that is after the pruning offset.




Error group: ParticipantErrorGroup / LedgerApiErrors / RequestValidation / NotFound
---------------------------------------





Error code: LEDGER_CONFIGURATION_NOT_FOUND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The ledger configuration could not be retrieved. This could happen due to incomplete initialization of the participant or due to an internal system error.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Contact the participant operator.



Error code: PACKAGE_NOT_FOUND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: This rejection is given when a read request tries to access a package which does not exist on the ledger.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Use a package id pertaining to a package existing on the ledger.



Error code: TRANSACTION_NOT_FOUND
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The transaction does not exist or the requesting set of parties are not authorized to fetch it.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Check the transaction id and verify that the requested transaction is visible to the requesting parties.




Error group: ParticipantErrorGroup / LedgerApiErrors / WriteServiceRejections
---------------------------------------





Error code: DISPUTED
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Depreciation**: Corresponds to transaction submission rejections that are not produced anymore.
    
    **Explanation**: An invalid transaction submission was not detected by the participant.

    **Category**: SystemInternalAssumptionViolated

    **Conveyance**: This error is logged with log-level ERROR on the server side. This error is exposed on the API with grpc-status INTERNAL without any details due to security reasons

    **Resolution**: Contact support.



Error code: OUT_OF_QUOTA
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Depreciation**: Corresponds to transaction submission rejections that are not produced anymore.
    
    **Explanation**: The Participant node did not have sufficient resource quota to submit the transaction.

    **Category**: ContentionOnSharedResources

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status ABORTED including a detailed error message

    **Resolution**: Inspect the error message and retry after after correcting the underlying issue.



Error code: PARTY_NOT_KNOWN_ON_LEDGER
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: One or more informee parties have not been allocated.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Check that all the informee party identifiers are correct, allocate all the informee parties, request their allocation or wait for them to be allocated before retrying the transaction submission.



Error code: SUBMITTER_CANNOT_ACT_VIA_PARTICIPANT
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: A submitting party is not authorized to act through the participant.

    **Category**: InsufficientPermission

    **Conveyance**: This error is logged with log-level WARN on the server side. This error is exposed on the API with grpc-status PERMISSION_DENIED without any details due to security reasons

    **Resolution**: Contact the participant operator or re-submit with an authorized party.



Error code: SUBMITTING_PARTY_NOT_KNOWN_ON_LEDGER
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    
    **Explanation**: The submitting party has not been allocated.

    **Category**: InvalidGivenCurrentSystemStateResourceMissing

    **Conveyance**: This error is logged with log-level INFO on the server side. This error is exposed on the API with grpc-status NOT_FOUND including a detailed error message

    **Resolution**: Check that the party identifier is correct, allocate the submitting party, request its allocation or wait for it to be allocated before retrying the transaction submission.


