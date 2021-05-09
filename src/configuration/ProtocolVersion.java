package configuration;

import exceptions.ArgsException;
import exceptions.ArgsException.Type;

public class ProtocolVersion {
    private short versionN, versionM;
    private String versionStr;

    private void construct(int versionN, int versionM) throws ArgsException {
        if (versionN < 0 || versionN > 9 || versionM < 0 || versionM > 9 || (versionN == 0 && versionM == 0)) 
            throw new ArgsException(Type.VERSION_NO, versionN + ", " + versionM);

        this.versionN = (short) versionN;
        this.versionM = (short) versionM;
    }

    public ProtocolVersion(int versionN, int versionM) throws ArgsException {
        construct(versionN, versionM);

        StringBuilder builder = new StringBuilder();
        builder.append(this.versionN);
        builder.append('.');
        builder.append(this.versionM);
        this.versionStr = builder.toString();
    }

    public ProtocolVersion(String version) throws ArgsException {
        String[] parts = version.split("\\.");
        if (parts.length != 2) throw new ArgsException(Type.VERSION_NO, version);

        construct(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        this.versionStr = version;
    }

    public short getN() {
        return versionN;
    }

    public short getM() {
        return versionM;
    }

    @Override
    public String toString() {
        return versionStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o != null && o.getClass() == this.getClass()) {
            ProtocolVersion version = (ProtocolVersion) o;
            return this.versionN == version.getN() && this.versionM == version.getM();
        }
        return false;
    }

    public boolean equals(String version) {
        return versionStr.equals(version);
    }
}
