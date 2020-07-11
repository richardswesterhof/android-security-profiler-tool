package defs;

public enum ERROR_CODES {

    INVALID_ARGUMENTS(1),
    INPUT_NOT_FOUND(2),
    WRONG_MODE(3),
    FILE_NOT_FOUND(4),
    INVALID_META_CSV(5),
    UNEXPECTED_ERROR(101),
    INTERNAL_ERROR(111);


    private int code;

    private ERROR_CODES(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
