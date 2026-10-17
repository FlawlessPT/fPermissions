package pt.flawless.fpermissions.model;

/** Config de grupos inválida; a mensagem está em português e diz qual o grupo. */
public class InvalidGroupsException extends Exception {

    public InvalidGroupsException(String message) {
        super(message);
    }
}
