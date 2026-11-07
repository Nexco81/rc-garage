package fr.nexco.rcgarage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import fr.nexco.rcgarage.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class RoomMigrationTest {
    private fun frozenDatabase(context: Context,name: String,version: Int): SupportSQLiteOpenHelper {
        // Frozen, processor-exported v1 schema: do not rebuild v1 from today's entities.
        val schema=JSONObject(javaClass.getResourceAsStream("/fr.nexco.rcgarage.data.GarageDatabase/$version.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object: SupportSQLiteOpenHelper.Callback(version) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities=schema.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val entity=entities.getJSONObject(i)
                        val table=entity.getString("tableName")
                        fun sql(query: String) = db.execSQL(query.replace("\${TABLE_NAME}",table))
                        sql(entity.getString("createSql"))
                        val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for(j in 0 until indices.length()) sql(indices.getJSONObject(j).getString("createSql"))
                    }
                    val setup=schema.getJSONArray("setupQueries")
                    for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase,oldVersion: Int,newVersion: Int) = error("Unexpected upgrade")
            }).build())
    }

    @Test fun frozenV1MigratesWithoutLosingRowsAndDestructiveFallbackIsForbidden() = runBlocking {
        val name="migration-test"
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val old=frozenDatabase(context,name,1)
        val vehicle=Vehicle(UUID.randomUUID(),"Test","Conservé")
        val equipment=Equipment(UUID.randomUUID(),EquipmentType.MOTOR,reference="Conservé",ownedQuantity=2)
        val assignment=EquipmentAssignment(UUID.randomUUID(),vehicle.id,equipment.id,EquipmentType.MOTOR)
        old.writableDatabase.apply {
            execSQL("INSERT INTO vehicles (id,brand,name,status) VALUES (?,?,?,?)",arrayOf<Any>(vehicle.id.toString(),vehicle.brand,vehicle.name,"UNKNOWN"))
            execSQL("INSERT INTO equipment (id,type,reference,ownedQuantity) VALUES (?,?,?,?)",arrayOf<Any>(equipment.id.toString(),"MOTOR","Conservé",2))
            execSQL("INSERT INTO assignments (id,vehicleId,equipmentId,role,quantity,isActive) VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(assignment.id.toString(),vehicle.id.toString(),equipment.id.toString(),"MOTOR",1,1))
        }
        old.close()
        val noMigration=Room.databaseBuilder(context,GarageDatabase::class.java,name).allowMainThreadQueries().build()
        var refused=false
        try { noMigration.garage().vehicles() } catch(_:IllegalStateException) { refused=true }
        finally { noMigration.close() }
        assertTrue("Missing migration must fail instead of destroying data",refused)
        val db=Room.databaseBuilder(context,GarageDatabase::class.java,name)
            .addMigrations(GarageDatabase.MIGRATION_1_2, GarageDatabase.MIGRATION_2_3).addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(vehicle),db.garage().vehicles())
            assertEquals(listOf(equipment),db.garage().equipment())
            assertEquals(listOf(assignment),db.garage().assignments())
            assertEquals(1,db.garage().availableQuantity(equipment.id))
            assertEquals(0,db.garage().sourceCount())
            var failed=false
            try { db.openHelper.writableDatabase.execSQL("UPDATE equipment SET ownedQuantity=-1") } catch(_:Exception) { failed=true }
            assertTrue(failed)
            assertEquals(3,db.openHelper.readableDatabase.version)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun v2UpgradeChangesGuardsWithoutChangingAnyBusinessRows() = runBlocking {
        val name="migration-v2-test"
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val old=frozenDatabase(context,name,2)
        val sql=old.writableDatabase
        val frozenGuards=org.json.JSONArray(javaClass.getResourceAsStream("/room-v2-stock-guards.json")!!
            .bufferedReader().use{it.readText()})
        for(i in 0 until frozenGuards.length()) sql.execSQL(frozenGuards.getString(i))
        val vehicles=listOf(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        vehicles.forEach { sql.execSQL("INSERT INTO vehicles(id,brand,name,status,notes) VALUES(?,?,?,?,?)",
            arrayOf<Any>(it.toString(),"Test","Préservé","UNKNOWN","Notes inchangées")) }
        val unknownRadio=UUID.randomUUID();val knownRadio=UUID.randomUUID();val motor=UUID.randomUUID()
        sql.execSQL("INSERT INTO equipment(id,type,reference) VALUES(?,?,?)",arrayOf<Any>(unknownRadio.toString(),"RADIO","Flysky G7P"))
        sql.execSQL("INSERT INTO equipment(id,type,reference,ownedQuantity) VALUES(?,?,?,?)",arrayOf<Any>(knownRadio.toString(),"RADIO","Flysky GT5",1))
        sql.execSQL("INSERT INTO equipment(id,type,reference,ownedQuantity) VALUES(?,?,?,?)",arrayOf<Any>(motor.toString(),"MOTOR","Moteur test",1))
        val knownAssignment=UUID.randomUUID()
        sql.execSQL("INSERT INTO assignments(id,vehicleId,equipmentId,role,quantity,isActive) VALUES(?,?,?,?,?,?)",
            arrayOf<Any>(knownAssignment.toString(),vehicles[0].toString(),knownRadio.toString(),"RADIO",1,1))
        val receipt=UUID.randomUUID()
        sql.execSQL("INSERT INTO import_sources(id,sha256,original,fileName,importedAt,reportJson) VALUES(?,?,?,?,?,?)",
            arrayOf<Any>(receipt.toString(),"test-fingerprint","Original exact".toByteArray(),"fixture.json",123L,"{}"))
        // Reproduce the v2 failure before upgrading.
        var rejected=false
        try {
            sql.execSQL("INSERT INTO assignments(id,vehicleId,equipmentId,role,quantity,isActive) VALUES(?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),vehicles[0].toString(),unknownRadio.toString(),"RADIO",1,1))
        } catch(_:Exception) { rejected=true }
        assertTrue(rejected)
        val schema=JSONObject(javaClass.getResourceAsStream("/fr.nexco.rcgarage.data.GarageDatabase/2.json")!!
            .bufferedReader().use{it.readText()}).getJSONObject("database").getJSONArray("entities")
        val tables=(0 until schema.length()).map{schema.getJSONObject(it).getString("tableName")}
        fun snapshot(database: SupportSQLiteDatabase): Map<String,List<List<String?>>> = tables.associateWith { table ->
            database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                buildList {
                    while(cursor.moveToNext()) add((0 until cursor.columnCount).map { col ->
                        if(cursor.getType(col)==android.database.Cursor.FIELD_TYPE_BLOB) cursor.getBlob(col).toList().toString()
                        else cursor.getString(col)
                    })
                }
            }
        }
        val before=snapshot(sql);old.close()
        val upgraded=Room.databaseBuilder(context,GarageDatabase::class.java,name)
            .addMigrations(GarageDatabase.MIGRATION_2_3).addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        try {
            assertEquals(before,snapshot(upgraded.openHelper.writableDatabase))
            assertEquals(3,upgraded.openHelper.readableDatabase.version)
            val dao=upgraded.garage()
            for(v in vehicles) dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),v,unknownRadio,EquipmentType.RADIO))
            assertNull(dao.equipment().single{it.id==unknownRadio}.ownedQuantity)
            assertEquals(3,dao.assignments().count{it.equipmentId==unknownRadio})
            dao.setOwnedQuantity(knownRadio,null) // v2 equipment_stock_update incorrectly rejected this.
            upgraded.openHelper.writableDatabase.execSQL("UPDATE assignments SET isActive=1 WHERE id=?",arrayOf<Any>(knownAssignment.toString()))
            assertNull(dao.equipment().single{it.id==knownRadio}.ownedQuantity)
            dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),vehicles[0],motor,EquipmentType.MOTOR))
            var overflowRejected=false
            try { dao.insertAssignment(EquipmentAssignment(UUID.randomUUID(),vehicles[1],motor,EquipmentType.MOTOR)) }
            catch(_:Exception) { overflowRejected=true }
            assertTrue(overflowRejected)
            assertArrayEquals("Original exact".toByteArray(),dao.source("test-fingerprint")!!.original)
        } finally { upgraded.close();context.deleteDatabase(name) }
    }

}
