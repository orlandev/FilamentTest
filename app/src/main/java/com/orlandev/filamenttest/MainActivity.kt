package com.orlandev.filamenttest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import com.google.android.filament.utils.translation
import com.orlandev.filamenttest.ui.theme.FilamentTestTheme
import com.orlandev.filamenttest.utils.*

class MainActivity : ComponentActivity() {

    private lateinit var engine: Engine
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader

    private lateinit var indirectLight: IndirectLight
    private lateinit var skybox: Skybox
    private var light: Int = 0

    companion object {
        init {
            Utils.init()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initFilament()

        setContent {
            FilamentTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(ScrollState(0)),
                            verticalArrangement = Arrangement.spacedBy(20.dp),

                            ) {

                            FilamentViewer("Car paint")

                            FilamentViewer("Carbon fiber")

                            FilamentViewer("Lacquered wood")

                            FilamentViewer("Wood")


                        }

                        Text(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            text = "Inmersoft 2023"
                        )
                    }


                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        engine.lightManager.destroy(light)
        engine.destroyEntity(light)
        engine.destroyIndirectLight(indirectLight)
        engine.destroySkybox(skybox)

        scenes.forEach {
            engine.destroyScene(it.value.scene)
            assetLoader.destroyAsset(it.value.asset)
        }

        assetLoader.destroy()
        resourceLoader.destroy()

        engine.destroy()
    }

    private fun initFilament() {
        engine = Engine.create()
        assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        val ibl = "courtyard_8k"
        readCompressedAsset(this, "envs/${ibl}/${ibl}_ibl.ktx").let {
            indirectLight = KTX1Loader.createIndirectLight(engine, it)
            indirectLight.intensity = 30_000.0f
        }

        readCompressedAsset(this, "envs/${ibl}/${ibl}_skybox.ktx").let {
            skybox = KTX1Loader.createSkybox(engine, it)
        }

        light = EntityManager.get().create()
        val (r, g, b) = Colors.cct(6_000.0f)
        LightManager.Builder(LightManager.Type.SUN).color(r, g, b).intensity(70_000.0f)
            .direction(0.28f, -0.6f, -0.76f).build(engine, light)

        fun createScene(name: String, gltf: String) {
            val scene = engine.createScene()
            val asset = readCompressedAsset(this, gltf).let {
                val asset = loadModelGlb(assetLoader, resourceLoader, it)
                transformToUnitCube(engine, asset)
                asset
            }
            scene.indirectLight = indirectLight
            scene.skybox = skybox

            scene.addEntities(asset.entities)

            scene.addEntity(light)

            scenes[name] = DataScene(engine, scene, asset)
        }

        createScene("Car paint", "models/car_paint/material_car_paint.glb")
        createScene("Carbon fiber", "models/carbon_fiber/material_carbon_fiber.glb")
        createScene("Lacquered wood", "models/lacquered_wood/material_lacquered_wood.glb")
        createScene("Wood", "models/wood/material_wood.glb")
    }
}


@Composable
fun FilamentViewer(name: String) {
    var modelViewer by remember { mutableStateOf<ModelViewer?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                modelViewer?.render(frameTimeNanos)
            }
        }
    }

    LaunchedEffect(Unit) {
        val (engine, scene, asset) = scenes[name]!!
        modelViewer?.scene = scene

        asset.entities.find {
            asset.getName(it)?.startsWith("car_paint_red") ?: false
        }?.also { entity ->
            val manager = engine.renderableManager
            val instance = manager.getInstance(entity)
            val material = manager.getMaterialInstanceAt(instance, 0)

            val color = Color(1.0f, 1.0f, 1.0f)

            val r = color.red
            val g = color.green
            val b = color.blue

            material.setParameter(
                "baseColorFactor", Colors.RgbaType.SRGB, r, g, b, 1.0f
            )
        }
    }

    AndroidView(modifier = Modifier
        .fillMaxWidth()
        .height(300.dp), factory = { context ->
        LayoutInflater.from(context).inflate(
            R.layout.filament_host, FrameLayout(context), false
        ).apply {
            val (engine) = scenes[name]!!
            engine.getCameraComponent(0).apply {
                translation(Float3(0f, 0f, -1000f))
            }
            modelViewer = ModelViewer(engine, this as SurfaceView).also {
                setupModelViewer(it)
            }
        }
    })
}
