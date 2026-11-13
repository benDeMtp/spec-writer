package fr.demo.facturation;

import fr.demo.contract.ContractStatus;

public class Invoicing {
    boolean billable(ContractStatus status) {
        return status == ContractStatus.ACTIVE;
    }
}
