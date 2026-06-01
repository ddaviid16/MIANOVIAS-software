-- =============================================================================
-- schema-tienda_vestidos.sql
--
-- Script de inicialización SEGURO e IDEMPOTENTE:
--   ✔  Crea la base de datos si no existe
--   ✔  Crea cada tabla SOLO si todavía no existe (CREATE TABLE IF NOT EXISTS)
--   ✔  Nunca elimina tablas ni borra datos existentes
--   ✔  Se puede ejecutar en instalaciones nuevas Y en reinstalaciones
-- =============================================================================

/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS,    UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- ── Base de datos ─────────────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS `tienda_vestidos`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `tienda_vestidos`;

-- ── Usuario y permisos ────────────────────────────────────────────────────────
CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY 'MIA1234';
GRANT ALL PRIVILEGES ON `tienda_vestidos`.* TO 'root'@'localhost';
FLUSH PRIVILEGES;

-- =============================================================================
-- TABLAS BASE (sin dependencias de FK)
-- =============================================================================

CREATE TABLE IF NOT EXISTS `asesor` (
  `numero_empleado` int NOT NULL,
  `nombre_completo` varchar(100) NOT NULL,
  `fecha_alta`      date DEFAULT NULL,
  `fecha_baja`      date DEFAULT NULL,
  `status`          enum('A','C') DEFAULT 'A',
  PRIMARY KEY (`numero_empleado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `clientes` (
  `telefono1`         varchar(15) NOT NULL,
  `telefono2`         varchar(15) NOT NULL,
  `nombre`            varchar(50) NOT NULL,
  `apellido_paterno`  varchar(50) NOT NULL,
  `apellido_materno`  varchar(50) DEFAULT NULL,
  `edad`              int NOT NULL,
  `como_se_entero`    enum('UBICACION','RECOMENDACION','GOOGLE MAPS','TIKTOK','FACEBOOK','INSTAGRAM') DEFAULT NULL,
  `fecha_evento`      date NOT NULL,
  `lugar_evento`      enum('HACIENDA','JARDIN','SALON','PLAYA') NOT NULL,
  `fecha_prueba1`     date DEFAULT NULL,
  `fecha_prueba2`     date DEFAULT NULL,
  `fecha_entrega`     date DEFAULT NULL,
  `busto`             decimal(5,2) NOT NULL,
  `cintura`           decimal(5,2) NOT NULL,
  `cadera`            decimal(5,2) NOT NULL,
  `status`            enum('A','C') NOT NULL DEFAULT 'A',
  `situacion_evento`  enum('CANCELA DEFINITIVO','POSPONE BODA INDEFINIDO','NORMAL') DEFAULT 'NORMAL',
  PRIMARY KEY (`telefono1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `inventarios` (
  `codigo_articulo` int NOT NULL,
  `articulo`        varchar(100) NOT NULL,
  `marca`           varchar(50) DEFAULT NULL,
  `modelo`          varchar(50) DEFAULT NULL,
  `talla`           varchar(10) DEFAULT NULL,
  `color`           varchar(30) DEFAULT NULL,
  `tipo`            enum('ARTICULO','OBSEQUIO') NOT NULL DEFAULT 'ARTICULO',
  `precio`          decimal(10,2) NOT NULL,
  `descuento`       decimal(5,2) DEFAULT '0.00',
  `existencia`      int DEFAULT '0',
  `fecha_registro`  date DEFAULT NULL,
  `status`          enum('A','C') DEFAULT 'A',
  PRIMARY KEY (`codigo_articulo`),
  KEY `idx_inventarios_tipo` (`tipo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `inventarioobsequios` (
  `codigo_articulo` int NOT NULL,
  `articulo`        varchar(100) NOT NULL,
  `marca`           varchar(50) DEFAULT NULL,
  `modelo`          varchar(50) DEFAULT NULL,
  `talla`           varchar(10) DEFAULT NULL,
  `color`           varchar(30) DEFAULT NULL,
  `precio`          decimal(10,2) NOT NULL DEFAULT '0.00',
  `descuento`       decimal(5,2) DEFAULT '0.00',
  `existencia`      int DEFAULT '0',
  `status`          enum('A','C') NOT NULL DEFAULT 'A',
  `fecha_registro`  date NOT NULL,
  PRIMARY KEY (`codigo_articulo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `folios` (
  `tipo`    varchar(5)  NOT NULL,
  `prefijo` varchar(16) NOT NULL,
  `ultimo`  int NOT NULL,
  PRIMARY KEY (`tipo`),
  UNIQUE KEY `ux_folios_prefijo` (`prefijo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `empresa` (
  `numero_empresa` int NOT NULL,
  `razon_social`   varchar(100) NOT NULL,
  `nombre_fiscal`  varchar(100) DEFAULT NULL,
  `rfc`            varchar(13) DEFAULT NULL,
  `calle_numero`   varchar(150) DEFAULT NULL,
  `colonia`        varchar(100) DEFAULT NULL,
  `codigo_postal`  char(5) DEFAULT NULL,
  `ciudad`         varchar(100) DEFAULT NULL,
  `estado`         varchar(100) DEFAULT NULL,
  `whatsapp`       varchar(15) DEFAULT NULL,
  `telefono`       varchar(15) DEFAULT NULL,
  `instagram`      varchar(100) DEFAULT NULL,
  `facebook`       varchar(100) DEFAULT NULL,
  `tiktok`         varchar(100) DEFAULT NULL,
  `correo`         varchar(100) DEFAULT NULL,
  `pagina_web`     varchar(150) DEFAULT NULL,
  `logo`           longblob,
  PRIMARY KEY (`numero_empresa`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `empresa_condiciones` (
  `id`                  int NOT NULL,
  `texto`               text DEFAULT (
'AL REALIZAR TU APARTADO DE VESTIDO, ESTAS ENTERADA DE QUE NO HABRA CAMBIOS O DEVOLUCIONES, SI POR ALGUNA RAZON PERSONAL DECIDES CANCELAR EL APARTADO Y/O COMPRA DEL VESTIDO ESTAS DE ACUERDO QUE NO HABRA DEVOLUCION DE ANTICIPOS Y/O ABONOS APLICADOS A LA COMPRA DEL VESTIDO.

LOS AJUSTES PARA LA PERSONALIZACION DE TU VESTIDO (AJUSTE DE TALLE Y LARGO) SE PROGRAMAN UN MES ANTES DE LA FECHA DE TU EVENTO Y SE TE ENTREGARA ENTRE UN PERIODO DE 10 A 15 DIAS ANTES DE TU FECHA DE EVENTO.

EN CASO DE QUE MODIFIQUES LA FECHA DE TU EVENTO Y/O ESTES EMBARAZADA Y/O ESTES EN UN REGIMEN DE DIETA QUE MODIFIQUE TUS MEDIDAS CORPORALES, ES IMPORTANTE NOTIFICARLO VIA MENSAJE AL WHATSAPP {whatsapp} PARA PODER CONSIDERAR LOS CAMBIOS PERTINENTES.
'),
  `fecha_actualizacion` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `seguridad_app` (
  `id`        int PRIMARY KEY,
  `pass_hash` varbinary(64) NOT NULL,
  `salt`      varbinary(16) NOT NULL
);

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `cortes_caja` (
  `id`                    int NOT NULL AUTO_INCREMENT,
  `fecha`                 date NOT NULL,
  `tarjeta_debito`        decimal(12,2) DEFAULT NULL,
  `tarjeta_credito`       decimal(12,2) DEFAULT NULL,
  `american_express`      decimal(12,2) DEFAULT NULL,
  `transferencia_bancaria` decimal(12,2) DEFAULT NULL,
  `deposito_bancario`     decimal(12,2) DEFAULT NULL,
  `efectivo`              decimal(12,2) DEFAULT NULL,
  `retiros`               decimal(12,2) DEFAULT NULL,
  `efectivo_neto`         decimal(12,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cortes_fecha` (`fecha`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `gastos_caja` (
  `id`          int NOT NULL AUTO_INCREMENT,
  `ts`          datetime NOT NULL,
  `efectivo_dia` decimal(12,2) DEFAULT NULL,
  `retiro`      decimal(12,2) NOT NULL,
  `motivo`      varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `entregas_vestidos` (
  `id`            int NOT NULL AUTO_INCREMENT,
  `numero_nota`   int NOT NULL,
  `cliente`       varchar(150) NOT NULL,
  `articulo`      varchar(150) NOT NULL,
  `fecha_entrega` date NOT NULL,
  `entregado`     char(1) NOT NULL DEFAULT 'N',
  `entregado_ts`  datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `ventas_vendedor_diario` (
  `id`               int NOT NULL AUTO_INCREMENT,
  `fecha`            date NOT NULL,
  `numero_empleado`  int NOT NULL,
  `nombre`           varchar(150) NOT NULL,
  `ventas`           int NOT NULL,
  `created_at`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fecha_empleado` (`fecha`,`numero_empleado`),
  KEY `idx_fecha` (`fecha`),
  KEY `idx_empleado` (`numero_empleado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- TABLAS CON DEPENDENCIAS DE FK
-- =============================================================================

CREATE TABLE IF NOT EXISTS `notas` (
  `numero_nota`     int NOT NULL AUTO_INCREMENT,
  `fecha_registro`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `telefono`        varchar(15) DEFAULT NULL,
  `asesor`          int DEFAULT NULL,
  `tipo`            enum('CN','CR','AB','DV') NOT NULL,
  `total`           decimal(10,2) NOT NULL,
  `saldo`           decimal(10,2) NOT NULL DEFAULT '0.00',
  `status`          enum('A','C') DEFAULT 'A',
  `folio`           varchar(12) NOT NULL,
  `nota_relacionada` int DEFAULT NULL,
  PRIMARY KEY (`numero_nota`),
  KEY `fk_notas_cliente` (`telefono`),
  KEY `fk_notas_asesor` (`asesor`),
  KEY `idx_notas_rel` (`tipo`),
  CONSTRAINT `fk_notas_asesor`  FOREIGN KEY (`asesor`)   REFERENCES `asesor`   (`numero_empleado`),
  CONSTRAINT `fk_notas_cliente` FOREIGN KEY (`telefono`) REFERENCES `clientes` (`telefono1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `nota_detalle` (
  `id`              int NOT NULL AUTO_INCREMENT,
  `numero_nota`     int NOT NULL,
  `fecha_registro`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `articulo`        varchar(100) DEFAULT NULL,
  `marca`           varchar(50) DEFAULT NULL,
  `modelo`          varchar(50) DEFAULT NULL,
  `talla`           varchar(10) DEFAULT NULL,
  `color`           varchar(30) DEFAULT NULL,
  `precio`          decimal(10,2) DEFAULT NULL,
  `descuento`       decimal(5,2) DEFAULT '0.00',
  `subtotal`        decimal(10,2) NOT NULL,
  `fecha_evento`    date DEFAULT NULL,
  `telefono`        varchar(15) DEFAULT NULL,
  `codigo_articulo` int DEFAULT NULL,
  `status`          enum('A','C') DEFAULT 'A',
  `fecha_entrega`   date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `telefono` (`telefono`),
  KEY `codigo_articulo` (`codigo_articulo`),
  KEY `idx_det_nota` (`numero_nota`),
  CONSTRAINT `fk_det_nota`          FOREIGN KEY (`numero_nota`)     REFERENCES `notas`      (`numero_nota`) ON DELETE CASCADE,
  CONSTRAINT `nota_detalle_ibfk_1`  FOREIGN KEY (`telefono`)        REFERENCES `clientes`   (`telefono1`),
  CONSTRAINT `nota_detalle_ibfk_2`  FOREIGN KEY (`codigo_articulo`) REFERENCES `inventarios` (`codigo_articulo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `formas_pago` (
  `numero_nota`           int NOT NULL,
  `fecha_operacion`       date DEFAULT NULL,
  `tarjeta_credito`       decimal(10,2) DEFAULT NULL,
  `tarjeta_debito`        decimal(10,2) DEFAULT NULL,
  `american_express`      decimal(10,2) DEFAULT NULL,
  `transferencia_bancaria` decimal(10,2) DEFAULT NULL,
  `deposito_bancario`     decimal(10,2) DEFAULT NULL,
  `efectivo`              decimal(10,2) DEFAULT NULL,
  `devolucion`            decimal(12,2) DEFAULT NULL,
  `tipo_operacion`        enum('CR','CN','AB','DV') DEFAULT NULL,
  `status`                enum('A','C') DEFAULT 'A',
  `referencia_dv`         varchar(20) DEFAULT NULL,
  PRIMARY KEY (`numero_nota`),
  CONSTRAINT `fk_fp_notas` FOREIGN KEY (`numero_nota`) REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `cambios_fecha_evento` (
  `numero_nota`         int NOT NULL,
  `telefono`            varchar(15) DEFAULT NULL,
  `fecha_inicial_evento` date DEFAULT NULL,
  `nueva_fecha`         date DEFAULT NULL,
  `asesor`              int DEFAULT NULL,
  `status`              enum('A','C') DEFAULT 'A',
  `motivo_cambio`       text,
  PRIMARY KEY (`numero_nota`),
  KEY `telefono` (`telefono`),
  KEY `asesor` (`asesor`),
  CONSTRAINT `cambios_fecha_evento_ibfk_2` FOREIGN KEY (`telefono`) REFERENCES `clientes` (`telefono1`),
  CONSTRAINT `cambios_fecha_evento_ibfk_3` FOREIGN KEY (`asesor`)   REFERENCES `asesor`   (`numero_empleado`),
  CONSTRAINT `fk_cambios_fecha_evento`     FOREIGN KEY (`numero_nota`) REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `cancelacion_eventos` (
  `numero_nota`        int NOT NULL,
  `telefono`           varchar(15) DEFAULT NULL,
  `fecha_operacion`    date DEFAULT NULL,
  `monto_compras`      decimal(10,2) DEFAULT NULL,
  `monto_abonos`       decimal(10,2) DEFAULT NULL,
  `saldo_pendiente`    decimal(10,2) DEFAULT NULL,
  `motivo_cancelacion` text,
  `status`             enum('A','C') DEFAULT 'A',
  `situacion_evento`   enum('CANCELA DEFINITIVO','POSPONE BODA INDEFINIDO','NORMAL') DEFAULT NULL,
  PRIMARY KEY (`numero_nota`),
  KEY `telefono` (`telefono`),
  CONSTRAINT `cancelacion_eventos_ibfk_2` FOREIGN KEY (`telefono`)   REFERENCES `clientes` (`telefono1`),
  CONSTRAINT `fk_cancelacion_evento`      FOREIGN KEY (`numero_nota`) REFERENCES `notas`   (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `cancelados` (
  `numero_nota`        int NOT NULL,
  `fecha_cancelacion`  date DEFAULT NULL,
  `motivo_cancelacion` text,
  `status`             enum('A','C') DEFAULT 'A',
  `asesor`             int DEFAULT NULL,
  PRIMARY KEY (`numero_nota`),
  KEY `asesor` (`asesor`),
  CONSTRAINT `cancelados_ibfk_2`    FOREIGN KEY (`asesor`)      REFERENCES `asesor` (`numero_empleado`),
  CONSTRAINT `fk_cancelados_notas`  FOREIGN KEY (`numero_nota`) REFERENCES `notas`  (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `devoluciones` (
  `numero_nota_dv` int NOT NULL,
  `nota_origen`    int NOT NULL,
  `motivo`         varchar(500) DEFAULT NULL,
  `fecha`          timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `monto_usado`    decimal(10,2) DEFAULT '0.00',
  PRIMARY KEY (`numero_nota_dv`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `devoluciones_det` (
  `id`                  int NOT NULL AUTO_INCREMENT,
  `numero_nota_origen`  int NOT NULL,
  `codigo_articulo`     int NOT NULL,
  `cantidad`            int NOT NULL,
  `numero_nota_dv`      int NOT NULL,
  `motivo`              varchar(500) DEFAULT NULL,
  `fecha_registro`      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_origen_codigo` (`numero_nota_origen`,`codigo_articulo`),
  KEY `fk_devdet_dv`  (`numero_nota_dv`),
  KEY `fk_devdet_art` (`codigo_articulo`),
  CONSTRAINT `fk_devdet_art`    FOREIGN KEY (`codigo_articulo`)    REFERENCES `inventarios` (`codigo_articulo`),
  CONSTRAINT `fk_devdet_dv`     FOREIGN KEY (`numero_nota_dv`)     REFERENCES `notas` (`numero_nota`) ON DELETE CASCADE,
  CONSTRAINT `fk_devdet_origen` FOREIGN KEY (`numero_nota_origen`) REFERENCES `notas` (`numero_nota`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `factura_datos` (
  `numero_nota` int NOT NULL,
  `persona`     enum('PF','PM') NOT NULL,
  `rfc`         varchar(13) NOT NULL,
  `regimen`     varchar(4) NOT NULL,
  `uso_cfdi`    varchar(4) NOT NULL,
  `correo`      varchar(120) DEFAULT NULL,
  `created_at`  datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`numero_nota`),
  CONSTRAINT `fk_facturadatos_nota` FOREIGN KEY (`numero_nota`) REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `notas_memo` (
  `numero_nota` int NOT NULL,
  `memo`        longtext,
  PRIMARY KEY (`numero_nota`),
  CONSTRAINT `notas_memo_ibfk_1` FOREIGN KEY (`numero_nota`) REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `notas_observaciones` (
  `numero_nota`   int NOT NULL,
  `observaciones` text,
  `updated_at`    timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`numero_nota`),
  CONSTRAINT `notas_observaciones_ibfk_1` FOREIGN KEY (`numero_nota`) REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `obsequios` (
  `numero_nota`    int NOT NULL,
  `telefono`       varchar(15) DEFAULT NULL,
  `fecha_operacion` date DEFAULT NULL,
  `obsequio1`      varchar(100) DEFAULT NULL,
  `obsequio2`      varchar(100) DEFAULT NULL,
  `obsequio3`      varchar(100) DEFAULT NULL,
  `obsequio4`      varchar(100) DEFAULT NULL,
  `obsequio5`      varchar(100) DEFAULT NULL,
  `tipo_operacion` enum('CN','CR','AB','DV') DEFAULT NULL,
  `asesor`         int DEFAULT NULL,
  `status`         enum('A','C') DEFAULT 'A',
  `fecha_evento`   date DEFAULT NULL,
  `obsequio1_cod`  int DEFAULT NULL,
  `obsequio2_cod`  int DEFAULT NULL,
  `obsequio3_cod`  int DEFAULT NULL,
  `obsequio4_cod`  int DEFAULT NULL,
  `obsequio5_cod`  int DEFAULT NULL,
  PRIMARY KEY (`numero_nota`),
  KEY `telefono` (`telefono`),
  KEY `asesor` (`asesor`),
  KEY `fk_obs_cod1` (`obsequio1_cod`),
  KEY `fk_obs_cod2` (`obsequio2_cod`),
  KEY `fk_obs_cod3` (`obsequio3_cod`),
  KEY `fk_obs_cod4` (`obsequio4_cod`),
  KEY `fk_obs_cod5` (`obsequio5_cod`),
  CONSTRAINT `fk_obs_cod1`  FOREIGN KEY (`obsequio1_cod`) REFERENCES `inventarioobsequios` (`codigo_articulo`),
  CONSTRAINT `fk_obs_cod2`  FOREIGN KEY (`obsequio2_cod`) REFERENCES `inventarioobsequios` (`codigo_articulo`),
  CONSTRAINT `fk_obs_cod3`  FOREIGN KEY (`obsequio3_cod`) REFERENCES `inventarioobsequios` (`codigo_articulo`),
  CONSTRAINT `fk_obs_cod4`  FOREIGN KEY (`obsequio4_cod`) REFERENCES `inventarioobsequios` (`codigo_articulo`),
  CONSTRAINT `fk_obs_cod5`  FOREIGN KEY (`obsequio5_cod`) REFERENCES `inventarioobsequios` (`codigo_articulo`),
  CONSTRAINT `fk_obsequios`  FOREIGN KEY (`numero_nota`) REFERENCES `notas`    (`numero_nota`),
  CONSTRAINT `obsequios_ibfk_2` FOREIGN KEY (`telefono`)  REFERENCES `clientes` (`telefono1`),
  CONSTRAINT `obsequios_ibfk_3` FOREIGN KEY (`asesor`)    REFERENCES `asesor`   (`numero_empleado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `pago_notadv` (
  `id`                    int NOT NULL AUTO_INCREMENT,
  `numero_nota_destino`   int NOT NULL,
  `numero_nota_dv`        int NOT NULL,
  `monto`                 decimal(10,2) NOT NULL,
  `fecha_aplicacion`      date NOT NULL DEFAULT (curdate()),
  PRIMARY KEY (`id`),
  KEY `numero_nota_destino` (`numero_nota_destino`),
  KEY `numero_nota_dv`      (`numero_nota_dv`),
  CONSTRAINT `pago_notadv_ibfk_1` FOREIGN KEY (`numero_nota_destino`) REFERENCES `notas` (`numero_nota`),
  CONSTRAINT `pago_notadv_ibfk_2` FOREIGN KEY (`numero_nota_dv`)      REFERENCES `notas` (`numero_nota`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `pedidos` (
  `numero_nota`    int NOT NULL,
  `fecha_registro` date DEFAULT NULL,
  `articulo`       varchar(100) DEFAULT NULL,
  `marca`          varchar(50) DEFAULT NULL,
  `modelo`         varchar(50) DEFAULT NULL,
  `talla`          varchar(10) DEFAULT NULL,
  `color`          varchar(30) DEFAULT NULL,
  `precio`         decimal(10,2) DEFAULT NULL,
  `descuento`      decimal(5,2) DEFAULT '0.00',
  `fecha_evento`   date DEFAULT NULL,
  `telefono`       varchar(15) DEFAULT NULL,
  `codigo_articulo` int DEFAULT NULL,
  `status`         enum('A','C') DEFAULT 'A',
  `id`             int NOT NULL AUTO_INCREMENT,
  `en_tienda`      char(1) NOT NULL DEFAULT 'N',
  `en_tienda_ts`   datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `telefono` (`telefono`),
  KEY `codigo_articulo` (`codigo_articulo`),
  KEY `idx_pedidos_nota` (`numero_nota`),
  CONSTRAINT `pedidos_ibfk_1` FOREIGN KEY (`telefono`)        REFERENCES `clientes`   (`telefono1`),
  CONSTRAINT `pedidos_ibfk_2` FOREIGN KEY (`codigo_articulo`) REFERENCES `inventarios` (`codigo_articulo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- DATOS INICIALES (INSERT IGNORE = no falla si ya existen)
-- =============================================================================

-- Tipos de folio predeterminados
-- Nota: el usuario puede cambiar el prefijo desde el panel de configuración.
INSERT IGNORE INTO `folios` (tipo, prefijo, ultimo) VALUES
  ('CN', 'CN-', 0),
  ('CR', 'CR-', 0),
  ('AB', 'AB-', 0),
  ('DV', 'DV-', 0),
  ('CA', 'CA-', 0);

-- Registro inicial de condiciones de venta (usa el texto por defecto de la columna)
INSERT IGNORE INTO `empresa_condiciones` (id) VALUES (1);

-- =============================================================================
-- RESTAURAR CONFIGURACIÓN DE SESIÓN
-- =============================================================================

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
