package server;

class FilePart {
    private final byte[] data;
    private final int amount;

    public FilePart(byte[] data, int amount) {
        this.data = data;
        this.amount = amount;
    }

    public byte[] getData() {
        return data;
    }

    public int getAmount() {
        return amount;
    }
}
