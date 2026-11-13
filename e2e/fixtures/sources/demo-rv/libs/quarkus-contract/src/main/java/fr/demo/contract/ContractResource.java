package fr.demo.contract;

public class ContractResource {
    public ContractStatus status(String id) {
        return ContractStatus.ACTIVE;
    }
}
