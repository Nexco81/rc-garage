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
    @Test fun frozenV1MigratesWithoutLosingRowsAndDestructiveFallbackIsForbidden() = runBlocking {
        val name="migration-test"
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        // Frozen, processor-exported v1 schema: do not rebuild v1 from today's entities.
        val schema=JSONObject(javaClass.getResourceAsStream("/fr.nexco.rcgarage.data.GarageDatabase/1.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val old=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object: SupportSQLiteOpenHelper.Callback(1) {
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
            .addMigrations(GarageDatabase.MIGRATION_1_2).addCallback(GarageDatabase.INTEGRITY_CALLBACK).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(vehicle),db.garage().vehicles())
            assertEquals(listOf(equipment),db.garage().equipment())
            assertEquals(listOf(assignment),db.garage().assignments())
            assertEquals(1,db.garage().availableQuantity(equipment.id))
            assertEquals(0,db.garage().sourceCount())
            var failed=false
            try { db.openHelper.writableDatabase.execSQL("UPDATE equipment SET ownedQuantity=-1") } catch(_:Exception) { failed=true }
            assertTrue(failed)
            assertEquals(2,db.openHelper.readableDatabase.version)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
