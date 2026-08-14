package br.com.lc.nfse.tela;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Lê a empresa (prestador) do MySQL do LC ERP. SOMENTE LEITURA — nunca escreve. */
public class EmpresaDao {

    private final Config config;

    public EmpresaDao(Config config) {
        this.config = config;
    }

    public EmpresaInfo buscar(String empresaId) throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL não encontrado no classpath", e);
        }
        String sql = "SELECT e.cnpj, e.razao_social, e.fantasia, IFNULL(e.im,'') im, "
                + "e.crt, IFNULL(e.regime_tributario,'') regime, c.codigocidade ibge, c.nome cidade "
                + "FROM empresa e JOIN cidades c ON c.id = e.id_cidades WHERE e.id = ?";
        try (Connection con = DriverManager.getConnection(config.mysqlUrl(), config.mysqlUser, config.mysqlPass);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Empresa id " + empresaId + " não encontrada");
                }
                String cnpj = rs.getString("cnpj") == null ? "" : rs.getString("cnpj");
                return new EmpresaInfo(
                        cnpj,
                        cnpj.replaceAll("\\D", ""),
                        rs.getString("razao_social"),
                        rs.getString("fantasia"),
                        rs.getString("im"),
                        rs.getString("crt"),
                        rs.getString("regime"),
                        rs.getString("ibge"),
                        rs.getString("cidade"));
            }
        }
    }
}
