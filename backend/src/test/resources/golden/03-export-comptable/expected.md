# Monthly accounting export

## Summary

Accountants export closed months to the ERP as CSV.

## Use cases

### UC-01: Export a closed month

- **Actor**: accountant
- **Goal**: Send the month's entries to the ERP
- **Preconditions**:
  - The month is closed by the chief accountant
- **Main flow**:
  1. Select the month
  2. Start the export
  3. Download the CSV file
- **Alternatives**:
  - The month is not closed: the export is refused
- **Source**: need excerpt « export the monthly accounting entries to the ERP as a CSV file »

## Acceptance scenarios

### UC-01: Export a closed month

#### SC-01-01: Export of a closed month *(Assumption)*
- **As a** accountant
- **Given** September is closed
- **When** I export September
- **Then** a CSV file with September's entries is produced
- **And** the separator is a semicolon

#### SC-01-02: Month not closed
- **As a** accountant
- **Given** October is not closed
- **When** I export October
- **Then** the export is refused with a message saying the month is not closed

## Assumptions

- **H-01**: “What CSV separator does the ERP expect?”: Semicolon (default value, question skipped)

## Functional requirements

- **EF-01**: Only closed months can be exported.

## Technical requirements

- **ET-01**: The CSV file is UTF-8 encoded.
